(ns status-im.ui.screens.keycard-settings.subs
  (:require [re-frame.core :as re-frame]
            [status-im.utils.datetime :as utils.datetime]))

(re-frame/reg-sub
 :keycard-paired-on
 (fn [db]
   (some-> (get-in db [:hardwallet :secrets :paired-on])
           (utils.datetime/timestamp->year-month-day-date))))

(re-frame/reg-sub
 :hardwallet/pairing
 (fn [db]
   (get-in db [:hardwallet :secrets :pairing])))

(re-frame/reg-sub
 :hardwallet/pin-retry-counter
 (fn [db]
   (get-in db [:hardwallet :application-info :pin-retry-counter])))

(re-frame/reg-sub
 :hardwallet/puk-retry-counter
 (fn [db]
   (get-in db [:hardwallet :application-info :puk-retry-counter])))

