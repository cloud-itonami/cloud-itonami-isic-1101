(ns distilling.store
  "MemStore for batch spirits data. In production, this will be swapped out
  for a Datomic or kotoba-server backend. Today, it's a simple in-memory map
  keyed by batch-id."
  (:require [clojure.string :as str]))

(defprotocol Store
  (batch-by-id [store batch-id] "Retrieve a spirits batch by ID")
  (add-batch [store batch-id batch-data] "Add or update a batch record")
  (mark-batch-processed [store batch-id] "Mark a batch as logged into production")
  (batch-already-processed? [store batch-id] "Check if batch was already logged")
  (mark-shipment-finalized [store batch-id] "Mark a batch's shipment as finalized")
  (batch-shipment-finalized? [store batch-id] "Check if shipment was already finalized"))

;; In-memory implementation for testing/dev
(defrecord MemStore [batches processed shipment-finalized]
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
    (contains? @shipment-finalized batch-id)))

(defn create-mem-store
  "Create an in-memory store for development/testing."
  []
  (MemStore.
   (atom {})
   (atom #{})
   (atom #{})))
