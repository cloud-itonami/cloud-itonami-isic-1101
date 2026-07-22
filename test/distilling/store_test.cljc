(ns distilling.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [distilling.store :as store]))

(deftest mem-store-basics
  (let [s (store/create-mem-store)]
    (testing "add and retrieve a batch"
      (store/add-batch s "batch-001" {:spirit-type "bourbon" :proof-us 100.0})
      (is (= "bourbon" (-> (store/batch-by-id s "batch-001") :spirit-type))))

    (testing "batch-already-processed? initially false"
      (is (false? (store/batch-already-processed? s "batch-001"))))

    (testing "mark-batch-processed sets flag"
      (store/mark-batch-processed s "batch-001")
      (is (true? (store/batch-already-processed? s "batch-001"))))

    (testing "batch-shipment-finalized? initially false"
      (is (false? (store/batch-shipment-finalized? s "batch-001"))))

    (testing "mark-shipment-finalized sets flag"
      (store/mark-shipment-finalized s "batch-001")
      (is (true? (store/batch-shipment-finalized? s "batch-001"))))))

(deftest mem-store-multiple-batches
  (let [s (store/create-mem-store)]
    (store/add-batch s "batch-001" {:spirit-type "bourbon"})
    (store/add-batch s "batch-002" {:spirit-type "vodka"})
    (store/mark-batch-processed s "batch-001")

    (testing "processed flag per batch"
      (is (true? (store/batch-already-processed? s "batch-001")))
      (is (false? (store/batch-already-processed? s "batch-002"))))))

(deftest mem-store-ledger-append-only
  (testing "a freshly created store's ledger is empty"
    (let [s (store/create-mem-store)]
      (is (empty? (store/ledger s)))))

  (testing "append-ledger! appends in order and returns the fact"
    (let [s (store/create-mem-store)
          fact-1 {:t :committed :op :log-production-batch :subject "batch-001"}
          fact-2 {:t :governor-hold :op :log-production-batch :subject "batch-002"}
          returned (store/append-ledger! s fact-1)]
      (is (= fact-1 returned))
      (store/append-ledger! s fact-2)
      (is (= [fact-1 fact-2] (store/ledger s)))))

  (testing "ledger is per-store, not shared across store instances"
    (let [s1 (store/create-mem-store)
          s2 (store/create-mem-store)]
      (store/append-ledger! s1 {:t :committed :op :schedule-maintenance})
      (is (= 1 (count (store/ledger s1))))
      (is (empty? (store/ledger s2))))))
