(ns distilling.store
  "MemStore for batch spirits data. In production, this will be swapped out
  for a Datomic or kotoba-server backend. Today, it's a simple in-memory map
  keyed by batch-id.

  FIX (this commit): the append-only audit ledger (`ledger`/
  `append-ledger!`) is this actor's core missing plumbing before this
  fix -- no such function existed anywhere in the codebase at all,
  worse than the usual dead-code-ledger pattern in some sibling actors:
  here even the concept was entirely absent from `src/`, despite prose
  comments elsewhere implying one should exist.
  `distilling.operation`'s `:commit`/`:hold` graph nodes now append
  every committed/held/approval-rejected decision fact here, so a
  distillery's operating history (every `:log-production-batch`/
  `:schedule-maintenance`/`:flag-food-safety-concern`/
  `:coordinate-shipment` decision) is always a query over an immutable
  log -- the same discipline every sibling `cloud-itonami-isic-*`
  actor's ledger provides. The ledger stays append-only; all
  pre-existing accessors below are UNCHANGED.")

(defprotocol Store
  (batch-by-id [store batch-id] "Retrieve a spirits batch by ID")
  (add-batch [store batch-id batch-data] "Add or update a batch record")
  (mark-batch-processed [store batch-id] "Mark a batch as logged into production")
  (batch-already-processed? [store batch-id] "Check if batch was already logged")
  (mark-shipment-finalized [store batch-id] "Mark a batch's shipment as finalized")
  (batch-shipment-finalized? [store batch-id] "Check if shipment was already finalized")
  (ledger [store] "The append-only audit ledger: every committed/held/approval-rejected decision fact, in append order.")
  (append-ledger! [store fact] "Append one immutable decision fact to the ledger. Returns the fact."))

;; In-memory implementation for testing/dev
(defrecord MemStore [batches processed shipment-finalized audit-ledger]
  Store
  (batch-by-id [_store batch-id]
    (get @batches batch-id))

  (add-batch [_store batch-id batch-data]
    (swap! batches assoc batch-id batch-data))

  (mark-batch-processed [_store batch-id]
    (swap! processed conj batch-id))

  (batch-already-processed? [_store batch-id]
    (contains? @processed batch-id))

  (mark-shipment-finalized [_store batch-id]
    (swap! shipment-finalized conj batch-id))

  (batch-shipment-finalized? [_store batch-id]
    (contains? @shipment-finalized batch-id))

  (ledger [_store]
    @audit-ledger)

  (append-ledger! [_store fact]
    (swap! audit-ledger conj fact)
    fact))

(defn create-mem-store
  "Create an in-memory store for development/testing."
  []
  (MemStore.
   (atom {})
   (atom #{})
   (atom #{})
   (atom [])))
