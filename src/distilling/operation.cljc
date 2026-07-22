(ns distilling.operation
  "OperationActor -- one spirits-distilling operation = one supervised actor
  run, expressed as a REAL compiled `langgraph-clj` `StateGraph`
  (`langgraph.graph/state-graph` + `compile-graph`). The advisor
  (DistillingAdvisor) is sealed into a single node (`:advise`); its
  proposal is ALWAYS routed through the independent Spirits Distilling
  Governor (`distilling.governor/check`, `:govern`) and the rollout
  phase gate (`distilling.phase/gate`, `:decide`) before anything
  commits to the SSoT.

  FIX (this commit): this namespace's OWN prior docstring admitted
  \"langgraph integration is deferred. This stub version...\" /
  \"Stub for building a langgraph-clj StateGraph\" -- `build` returned a
  hand-rolled closure over `run-operation` with ZERO
  `langgraph.graph/state-graph` or `interrupt-before` calls anywhere,
  despite `blueprint.edn` claiming `:itonami.blueprint/maturity
  :implemented` and `deps.edn`'s REAL `langgraph` dependency sitting
  unused under the `:dev :override-deps` alias (main `:deps` was `{}`).
  This is now the real thing: a genuinely compiled graph with real
  human-in-the-loop interrupt/resume.

  State machine:
  intake -> advise -> govern -> decide -+-> commit
                                         +-> request-approval -> commit
                                         +-> hold

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (`distilling.store/MemStore`, or any `Store` impl)
    - the Advisor  (mock | real LLM)
    - the Phase    (0->3 rollout, read per-request off `:context`, not
                     frozen at `build` time)

  ALL of `distilling.advisor`'s proposal logic and ALL of
  `distilling.governor`'s hard/escalate checks (`check`, `hold-fact`)
  and `distilling.phase`'s rollout gate (`verdict->disposition`,
  `gate`) are reused UNCHANGED -- this fix only wires the existing
  domain policy into a real compiled graph and a real ledger, it does
  not redesign the spirits-distilling compliance rules.

  One graph run = one spirits-distilling operation (batch-proposal ->
  advise -> govern -> decide -> commit | hold | approval). No unbounded
  inner loop -- each operation is auditable and checkpointed. A batch's
  life is advanced by MANY operations (flag-concern / log-batch /
  shipment), each its own independent run.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` GENUINELY pauses (checkpointed)
  the actor at the `:request-approval` node and hands the decision to a
  human operator (master distiller / compliance officer). The approver
  resumes the SAME compiled graph/thread with
  `{:approval {:status :approved :by ...}}` (or `:rejected`).
  `:log-production-batch`/`:coordinate-shipment` ALWAYS reach this node
  when the Governor is clean -- see `distilling.governor/high-stakes` /
  `always-escalate-ops` and `distilling.phase`'s independent phase-gate
  agreement (phase 0/1/2 also escalate or hold these ops).

  Every commit/hold/approval-rejected decision fact lands in
  `distilling.store`'s append-only ledger (`store/append-ledger!`),
  genuinely wired into both the `:commit` and `:hold` terminal nodes --
  not test-only plumbing."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [distilling.advisor :as advisor]
            [distilling.governor :as governor]
            [distilling.phase :as phase]
            [distilling.store :as store]))

;; ============================================================================
;; Audit-fact builders
;; ============================================================================

(defn- commit-fact [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:subject request)]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

;; ============================================================================
;; Compiled StateGraph
;; ============================================================================

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a `distilling.advisor/Advisor` (default: mock-advisor)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)

  The compiled graph's input map: `{:request .. :context ..}` (context,
  e.g. `{:actor-id .. :phase ..}`, is per-request, not frozen at
  `build` time -- matches the pre-graph `run-operation`'s call-time
  argument)."
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request  {:default nil}
         :context  {:default {}}
         :proposal {:default nil}
         :verdict  {:default nil}
         :decision {:default nil}
         :approval {:default nil}
         :record   {:default false}
         :audit    {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [proposal (advisor/-advise advisor store request)]
            {:proposal proposal
             :audit    [(advisor/trace request proposal)]})))

      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context verdict]}]
          (let [base-disposition (phase/verdict->disposition verdict)
                ph               (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base-disposition)]
            (case disposition
              :commit
              {:decision :commit}

              :escalate
              {:decision :escalate
               :audit [{:t       :approval-requested
                        :op      (:op request)
                        :subject (:subject request)
                        :reason  (or reason
                                     (cond (:high-stakes? verdict) :actuation
                                           :else                   :low-confidence))}]}

              :hold
              {:decision :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}))))

      (g/add-node :request-approval
        (fn [{:keys [request context approval]}]
          (if (= :approved (:status approval))
            {:decision :commit
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject request) :by (:by approval)}]}
            {:decision :hold
             :audit [{:t          :approval-rejected
                      :op         (:op request)
                      :actor      (:actor-id context)
                      :subject    (:subject request)
                      :disposition :hold
                      :by         (:by approval)}]})))

      (g/add-node :commit
        (fn [{:keys [request context proposal approval]}]
          (let [f (cond-> (commit-fact request context proposal)
                    approval (assoc :approved-by (:by approval)))]
            (store/append-ledger! store f)
            {:record (commit-record request context proposal)
             :audit  [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store hf))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [decision]}]
          (case decision
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [decision]}]
          (if (= :commit decision) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
