(ns status-im.hardwallet.core
  (:require [re-frame.core :as re-frame]
            status-im.hardwallet.fx
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.config :as config]
            [status-im.utils.fx :as fx]
            [status-im.utils.platform :as platform]
            [taoensso.timbre :as log]
            [status-im.i18n :as i18n]
            [status-im.accounts.create.core :as accounts.create]
            [status-im.accounts.login.core :as accounts.login]
            [status-im.node.core :as node]))

(defonce default-pin "000000")

(defn hardwallet-supported? [{:keys [db]}]
  (and config/hardwallet-enabled?
       platform/android?
       (get-in db [:hardwallet :nfc-supported?])))

(fx/defn navigate-to-authentication-method
  [cofx]
  (if (hardwallet-supported? cofx)
    (navigation/navigate-to-cofx cofx :hardwallet-authentication-method nil)
    (accounts.create/navigate-to-create-account-screen cofx)))

(fx/defn on-register-card-events
  [{:keys [db]} listeners]
  {:db (update-in db [:hardwallet :listeners] merge listeners)})

(fx/defn on-get-application-info-success
  [{:keys [db] :as cofx} info]
  (let [info' (js->clj info :keywordize-keys true)
        {:keys [pin-retry-counter puk-retry-counter]} info'
        enter-step (if (zero? pin-retry-counter) :puk :current)]
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:hardwallet :pin :enter-step] enter-step)
                       (assoc-in [:hardwallet :application-info] info')
                       (assoc-in [:hardwallet :application-info :applet-installed?] true)
                       (assoc-in [:hardwallet :application-info-error] nil))}
              (when (zero? puk-retry-counter)
                (navigation/navigate-to-cofx :keycard-settings nil)))))

(fx/defn on-get-application-info-error
  [{:keys [db]} error]
  (log/debug "[hardwallet] application info error " error)
  {:db (-> db
           (assoc-in [:hardwallet :application-info-error] error)
           (assoc-in [:hardwallet :application-info :applet-installed?] false))})

(fx/defn set-nfc-support
  [{:keys [db]} supported?]
  {:db (assoc-in db [:hardwallet :nfc-supported?] supported?)})

(fx/defn set-nfc-enabled
  [{:keys [db]} enabled?]
  {:db (assoc-in db [:hardwallet :nfc-enabled?] enabled?)})

(fx/defn status-hardwallet-option-pressed [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:hardwallet/check-nfc-enabled    nil
             :hardwallet/register-card-events nil
             :db                              (-> db
                                                  (assoc-in [:hardwallet :setup-step] :begin)
                                                  (assoc-in [:hardwallet :on-card-connected] nil)
                                                  (assoc-in [:hardwallet :pin :on-verified] nil))}
            (navigation/navigate-to-cofx :hardwallet-connect nil)))

(fx/defn success-button-pressed [cofx]
  ;; login not implemented yet
)

(fx/defn navigate-to-enter-pin-screen
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db (assoc-in db [:hardwallet :pin :current] [])}
            (navigation/navigate-to-cofx :enter-pin nil)))

(fx/defn change-pin-pressed
  [{:keys [db] :as cofx}]
  (let [card-connected? (get-in db [:hardwallet :card-connected?])
        pin-retry-counter (get-in db [:hardwallet :application-info :pin-retry-counter])
        enter-step (if (zero? pin-retry-counter) :puk :current)]
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:hardwallet :on-card-connected] :hardwallet/navigate-to-enter-pin-screen)
                       (assoc-in [:hardwallet :pin] {:enter-step   enter-step
                                                     :current      []
                                                     :puk          []
                                                     :original     []
                                                     :confirmation []
                                                     :status       nil
                                                     :error-label  nil
                                                     :on-verified  :hardwallet/proceed-to-change-pin}))}
              (if card-connected?
                (navigation/navigate-to-cofx :enter-pin nil)
                (navigation/navigate-to-cofx :hardwallet-connect nil)))))

(fx/defn proceed-to-change-pin
  [{:keys [db]}]
  {:db (-> db
           (assoc-in [:hardwallet :pin :enter-step] :original)
           (assoc-in [:hardwallet :pin :status] nil))})

(fx/defn unpair-card-pressed
  [_]
  {:ui/show-confirmation {:title               (i18n/label :t/unpair-card)
                          :content             (i18n/label :t/unpair-card-confirmation)
                          :confirm-button-text (i18n/label :t/yes)
                          :cancel-button-text  (i18n/label :t/no)
                          :on-accept           #(re-frame/dispatch [:keycard-settings.ui/unpair-card-confirmed])
                          :on-cancel           #()}})

(fx/defn unpair-card-confirmed
  [{:keys [db] :as cofx}]
  (let [card-connected? (get-in db [:hardwallet :card-connected?])
        pin-retry-counter (get-in db [:hardwallet :application-info :pin-retry-counter])
        enter-step (if (zero? pin-retry-counter) :puk :current)]
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:hardwallet :on-card-connected] :hardwallet/navigate-to-enter-pin-screen)
                       (assoc-in [:hardwallet :pin] {:enter-step  enter-step
                                                     :current     []
                                                     :puk         []
                                                     :status      nil
                                                     :error-label nil
                                                     :on-verified :hardwallet/unpair}))}
              (if card-connected?
                (navigation/navigate-to-cofx :enter-pin nil)
                (navigation/navigate-to-cofx :hardwallet-connect nil)))))

(fx/defn unpair
  [{:keys [db]}]
  (let [pin (apply str (get-in db [:hardwallet :pin :current]))
        pairing (get-in db [:hardwallet :secrets :pairing])]
    {:hardwallet/unpair {:pin     pin
                         :pairing pairing}}))

(fx/defn settings-screen-did-mount
  [{:keys [db]}]
  {:db (-> db
           (assoc-in [:hardwallet :on-card-connected] nil)
           (assoc-in [:hardwallet :pin :on-verified] nil))})

(fx/defn unpair-and-delete
  [{:keys [db]}]
  (let [pin (apply str (get-in db [:hardwallet :pin :current]))
        pairing (get-in db [:hardwallet :secrets :pairing])]
    {:hardwallet/unpair-and-delete {:pin     pin
                                    :pairing pairing}}))

(fx/defn on-delete-success
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db                              (-> db
                                                  (assoc-in [:hardwallet :secrets] nil)
                                                  (assoc-in [:hardwallet :application-info] nil)
                                                  (assoc-in [:hardwallet :on-card-connected] nil)
                                                  (assoc-in [:hardwallet :pin] {:status      nil
                                                                                :error-label nil
                                                                                :on-verified nil}))
             :hardwallet/get-application-info nil
             :hardwallet/remove-pairing       nil
             :utils/show-popup                {:title   ""
                                               :content (i18n/label :t/card-reseted)}}
            (navigation/navigate-to-cofx :keycard-settings nil)))

(fx/defn on-delete-error
  [{:keys [db] :as cofx} error]
  (log/debug "[hardwallet] delete error" error)
  (fx/merge cofx
            {:db                              (-> db
                                                  (assoc-in [:hardwallet :on-card-connected] nil)
                                                  (assoc-in [:hardwallet :pin] {:status      nil
                                                                                :error-label nil
                                                                                :on-verified nil}))
             :hardwallet/get-application-info nil
             :utils/show-popup                {:title   ""
                                               :content (i18n/label :t/something-went-wrong)}}
            (navigation/navigate-to-cofx :keycard-settings nil)))

(fx/defn reset-card-pressed [cofx]
  (navigation/navigate-to-cofx cofx :reset-card nil))

(fx/defn reset-card-next-button-pressed
  [{:keys [db] :as cofx}]
  (let [card-connected? (get-in db [:hardwallet :card-connected?])
        puk-retry-counter (get-in db [:hardwallet :application-info :puk-retry-counter])
        pin-retry-counter (get-in db [:hardwallet :application-info :pin-retry-counter])
        enter-step (if (zero? pin-retry-counter) :puk :current)]
    (if (zero? puk-retry-counter)
      {:hardwallet/delete nil}
      (fx/merge cofx
                {:db (-> db
                         (assoc-in [:hardwallet :on-card-connected] :hardwallet/navigate-to-enter-pin-screen)
                         (assoc-in [:hardwallet :pin] {:enter-step  enter-step
                                                       :current     []
                                                       :puk         []
                                                       :status      nil
                                                       :error-label nil
                                                       :on-verified :hardwallet/unpair-and-delete}))}
                (if card-connected?
                  (navigation/navigate-to-cofx :enter-pin nil)
                  (navigation/navigate-to-cofx :hardwallet-connect nil))))))

(fx/defn error-button-pressed [{:keys [db] :as cofx}]
  (let [return-to-step (get-in db [:hardwallet :return-to-step] :begin)]
    (fx/merge cofx
              {:db (assoc-in db [:hardwallet :setup-step] return-to-step)}
              (when-not return-to-step
                (navigation/navigate-to-cofx :hardwallet-connect nil)))))

(fx/defn load-pairing-screen [{:keys [db]}]
  {:db       (assoc-in db [:hardwallet :setup-step] :pairing)
   :dispatch [:hardwallet/pair]})

(fx/defn pair [cofx]
  (let [{:keys [password]} (get-in cofx [:db :hardwallet :secrets])]
    {:hardwallet/pair {:password password}}))

(fx/defn return-back-from-nfc-settings [{:keys [db]}]
  (when (= :hardwallet-connect (:view-id db))
    {:hardwallet/check-nfc-enabled nil}))

(defn- proceed-to-pin-confirmation [fx]
  (assoc-in fx [:db :hardwallet :pin :enter-step] :confirmation))

(fx/defn pin-match
  [{:keys [db] :as fx}]
  (let [pairing (get-in db [:hardwallet :secrets :pairing])
        new-pin (apply str (get-in db [:hardwallet :pin :original]))
        current-pin (apply str (get-in db [:hardwallet :pin :current]))]
    (fx/merge fx
              {:db                    (assoc-in db [:hardwallet :pin :status] :verifying)
               :hardwallet/change-pin {:new-pin     new-pin
                                       :current-pin current-pin
                                       :pairing     pairing}})))

(defn- pin-mismatch [fx]
  (assoc-in fx [:db :hardwallet :pin] {:status       :error
                                       :error-label  :t/pin-mismatch
                                       :original     []
                                       :confirmation []
                                       :enter-step   :original}))

(fx/defn dispatch-on-verified-event
  [{:keys [db]} event]
  {:dispatch [event]
   :db       (assoc-in db [:hardwallet :pin :on-verified] nil)})

(fx/defn on-unblock-pin-success
  [{:keys [db] :as cofx}]
  (let [pairing (get-in db [:hardwallet :secrets :pairing])]
    (fx/merge cofx
              {:hardwallet/get-application-info pairing
               :utils/show-popup                {:title   (i18n/label :t/pin-unblocked)
                                                 :content (i18n/label :t/pin-unblocked-description {:pin default-pin})}
               :db                              (-> db
                                                    (update-in [:hardwallet :pin] merge {:status      nil
                                                                                         :enter-step :current
                                                                                         :puk         nil
                                                                                         :error-label nil}))}
              (navigation/navigate-to-cofx :keycard-settings nil))))

(defn on-unblock-pin-error
  [{:keys [db]} error]
  (let [pairing (get-in db [:hardwallet :secrets :pairing])]
    (log/debug "[hardwallet] unblock pin error" error)
    {:hardwallet/get-application-info pairing
     :db                              (update-in db [:hardwallet :pin] merge {:status      :error
                                                                              :error-label :t/puk-mismatch
                                                                              :enter-step  :puk
                                                                              :puk         nil})}))

(fx/defn on-verify-pin-success
  [{:keys [db] :as cofx}]
  (let [on-verified (get-in db [:hardwallet :pin :on-verified])
        pairing (get-in db [:hardwallet :secrets :pairing])]
    (fx/merge cofx
              {:hardwallet/get-application-info pairing
               :db                              (-> db
                                                    (update-in [:hardwallet :pin] merge {:status      nil
                                                                                         :error-label nil}))}
              (when on-verified
                (dispatch-on-verified-event on-verified)))))

(defn on-verify-pin-error
  [{:keys [db]} error]
  (let [pairing (get-in db [:hardwallet :secrets :pairing])
        pin-retry-counter (get-in db [:hardwallet :application-info :pin-retry-counter])
        ;enter-step (if (<= pin-retry-counter 1) :puk :current)
]
    (log/debug "[hardwallet] verify pin error" error)
    {:hardwallet/get-application-info pairing
     :db                              (update-in db [:hardwallet :pin] merge {:status       :error
                                                                              :error-label  :t/pin-mismatch
                                                                              :enter-step   :current
                                                                              :current      []
                                                                              :original     []
                                                                              :confirmation []})}))

(fx/defn on-change-pin-success
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db               (-> db
                                   (assoc-in [:hardwallet :on-card-connected] nil)
                                   (assoc-in [:hardwallet :pin] {:status      nil
                                                                 :error-label nil}))
             :utils/show-popup {:title   ""
                                :content (i18n/label :t/pin-changed)}}
            (navigation/navigate-to-cofx :keycard-settings nil)))

(fx/defn on-change-pin-error
  [{:keys [db]} error]
  (log/debug "[hardwallet] change pin error" error)
  {:db (update-in db [:hardwallet :pin] merge {:status       :error
                                               :error-label  :t/pin-mismatch
                                               :enter-step   :original
                                               :confirmation []
                                               :original     []})})

(fx/defn on-unpair-success
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db                              (-> db
                                                  (assoc-in [:hardwallet :secrets] nil)
                                                  (assoc-in [:hardwallet :on-card-connected] nil)
                                                  (assoc-in [:hardwallet :pin] {:status      nil
                                                                                :error-label nil
                                                                                :on-verified nil}))
             :hardwallet/get-application-info nil
             :hardwallet/remove-pairing       nil
             :utils/show-popup                {:title   ""
                                               :content (i18n/label :t/card-unpaired)}}
            (navigation/navigate-to-cofx :keycard-settings nil)))

(fx/defn on-unpair-error
  [{:keys [db] :as cofx} error]
  (log/debug "[hardwallet] unpair error" error)
  (fx/merge cofx
            {:db                              (-> db
                                                  (assoc-in [:hardwallet :on-card-connected] nil)
                                                  (assoc-in [:hardwallet :pin] {:status      nil
                                                                                :error-label nil
                                                                                :on-verified nil}))
             :hardwallet/get-application-info nil
             :utils/show-popup                {:title   ""
                                               :content (i18n/label :t/something-went-wrong)}}
            (navigation/navigate-to-cofx :keycard-settings nil)))

(defn- verify-pin
  [{:keys [db] :as fx}]
  (let [pin (apply str (get-in fx [:db :hardwallet :pin :current]))
        pairing (get-in fx [:db :hardwallet :secrets :pairing])]
    {:db                    (assoc-in db [:hardwallet :pin :status] :verifying)
     :hardwallet/verify-pin {:pin     pin
                             :pairing pairing}}))

(defn- unblock-pin
  [{:keys [db] :as fx}]
  (let [puk (apply str (get-in fx [:db :hardwallet :pin :puk]))
        pairing (get-in fx [:db :hardwallet :secrets :pairing])]
    {:db                     (assoc-in db [:hardwallet :pin :status] :verifying)
     :hardwallet/unblock-pin {:puk     puk
                              :new-pin default-pin
                              :pairing pairing}}))

(fx/defn process-pin-input
  [{:keys [db]} number enter-step]
  (let [db' (update-in db [:hardwallet :pin enter-step] conj number)
        numbers-entered (count (get-in db' [:hardwallet :pin enter-step]))]
    (cond-> {:db (assoc-in db' [:hardwallet :pin :status] nil)}
      (and (= enter-step :original)
           (= 6 numbers-entered))
      (proceed-to-pin-confirmation)

      (and (= enter-step :current)
           (= 6 numbers-entered))
      (verify-pin)

      (and (= enter-step :puk)
           (= 12 numbers-entered))
      (unblock-pin)

      (and (= enter-step :confirmation)
           (= (get-in db' [:hardwallet :pin :original])
              (get-in db' [:hardwallet :pin :confirmation])))
      (pin-match)

      (and (= enter-step :confirmation)
           (= 6 numbers-entered)
           (not= (get-in db' [:hardwallet :pin :original])
                 (get-in db' [:hardwallet :pin :confirmation])))
      (pin-mismatch))))

(fx/defn load-loading-keys-screen
  [{:keys [db]}]
  {:db       (assoc-in db [:hardwallet :setup-step] :loading-keys)
   :dispatch [:hardwallet/generate-and-load-key]})

(fx/defn load-generating-mnemonic-screen
  [{:keys [db]}]
  {:db       (assoc-in db [:hardwallet :setup-step] :generating-mnemonic)
   :dispatch [:hardwallet/generate-mnemonic]})

(fx/defn generate-mnemonic
  [cofx]
  (let [{:keys [pairing]} (get-in cofx [:db :hardwallet :secrets])]
    {:hardwallet/generate-mnemonic {:pairing pairing}}))

(fx/defn dispatch-on-card-connected-event
  [{:keys [db]} event]
  {;:db       (assoc-in db [:hardwallet :on-card-connected] nil)
   :dispatch [event]})

(fx/defn on-card-connected
  [{:keys [db] :as cofx} data]
  (log/debug "[hardwallet] card connected " data)
  (let [return-to-step (get-in db [:hardwallet :return-to-step])
        setup-running? (get-in db [:hardwallet :setup-step])
        on-card-connected (get-in db [:hardwallet :on-card-connected])
        pairing (get-in db [:hardwallet :secrets :pairing])]
    (fx/merge cofx
              {:db                              (cond-> db
                                                  return-to-step (assoc-in [:hardwallet :setup-step] return-to-step)
                                                  true (assoc-in [:hardwallet :card-connected?] true)
                                                  true (assoc-in [:hardwallet :return-to-step] nil))
               :hardwallet/get-application-info pairing}
              (when on-card-connected
                (dispatch-on-card-connected-event on-card-connected))
              (when setup-running?
                (navigation/navigate-to-cofx :hardwallet-setup nil)))))

(fx/defn on-card-disconnected
  [{:keys [db] :as cofx} _]
  (log/debug "[hardwallet] card disconnected ")
  (let [setup-running? (get-in db [:hardwallet :setup-step])
        on-card-connected (get-in db [:hardwallet :on-card-connected])]
    (fx/merge cofx
              {:db (assoc-in db [:hardwallet :card-connected?] false)}
              (when (or setup-running?
                        on-card-connected)
                (navigation/navigate-to-cofx :hardwallet-connect nil)))))

(fx/defn load-preparing-screen
  [{:keys [db]}]
  {:db       (assoc-in db [:hardwallet :setup-step] :preparing)
   :dispatch [:hardwallet/install-applet-and-init-card]})

(fx/defn install-applet-and-init-card
  [{:keys [db]}]
  {:hardwallet/install-applet-and-init-card nil})

(fx/defn on-install-applet-and-init-card-success
  [{:keys [db]} secrets]
  (let [secrets' (js->clj secrets :keywordize-keys true)]
    {:db (-> db
             (assoc-in [:hardwallet :setup-step] :secret-keys)
             (assoc-in [:hardwallet :secrets] secrets'))}))

(defn- tag-lost-exception? [code]
  (= code "android.nfc.TagLostException"))

(fx/defn process-error [{:keys [db] :as cofx} code]
  (if (tag-lost-exception? code)
    (navigation/navigate-to-cofx cofx :hardwallet-connect nil)
    {:db (assoc-in db [:hardwallet :setup-step] :error)}))

(fx/defn on-install-applet-and-init-card-error
  [{:keys [db] :as cofx} {:keys [code error]}]
  (log/debug "[hardwallet] install applet and init card error: " error)
  (fx/merge cofx
            {:db (-> db
                     (assoc-in [:hardwallet :return-to-step] :begin)
                     (assoc-in [:hardwallet :setup-error] error))}
            (process-error code)))

(fx/defn on-pairing-success
  [{:keys [db]} pairing]
  {:hardwallet/persist-pairing pairing
   :db                         (-> db
                                   (assoc-in [:hardwallet :setup-step] :card-ready)
                                   (assoc-in [:hardwallet :secrets :pairing] pairing))})

(fx/defn on-pairing-error
  [{:keys [db] :as cofx} {:keys [error code]}]
  (log/debug "[hardwallet] pairing error: " error)
  (fx/merge cofx
            {:db (-> db
                     (assoc-in [:hardwallet :return-to-step] :secret-keys)
                     (assoc-in [:hardwallet :setup-error] error))}
            (process-error code)))

(fx/defn on-generate-mnemonic-success
  [{:keys [db]} mnemonic]
  {:db (-> db
           (assoc-in [:hardwallet :setup-step] :recovery-phrase)
           (assoc-in [:hardwallet :secrets :mnemonic] mnemonic))})

(fx/defn on-generate-mnemonic-error
  [{:keys [db] :as cofx} {:keys [error code]}]
  (log/debug "[hardwallet] generate mnemonic error: " error)
  (fx/merge cofx
            {:db (-> db
                     (assoc-in [:hardwallet :return-to-step] :card-ready)
                     (assoc-in [:hardwallet :setup-error] error))}
            (process-error code)))

(fx/defn recovery-phrase-start-confirmation [{:keys [db]}]
  (let [mnemonic (get-in db [:hardwallet :secrets :mnemonic])
        [word1 word2] (shuffle (map-indexed vector (clojure.string/split mnemonic #" ")))
        word1 (zipmap [:idx :word] word1)
        word2 (zipmap [:idx :word] word2)]
    {:db (-> db
             (assoc-in [:hardwallet :setup-step] :recovery-phrase-confirm-word1)
             (assoc-in [:hardwallet :recovery-phrase :step] :word1)
             (assoc-in [:hardwallet :recovery-phrase :confirm-error] nil)
             (assoc-in [:hardwallet :recovery-phrase :input-word] nil)
             (assoc-in [:hardwallet :recovery-phrase :word1] word1)
             (assoc-in [:hardwallet :recovery-phrase :word2] word2))}))

(defn- show-recover-confirmation []
  {:ui/show-confirmation {:title               (i18n/label :t/are-you-sure?)
                          :content             (i18n/label :t/are-you-sure-description)
                          :confirm-button-text (clojure.string/upper-case (i18n/label :t/yes))
                          :cancel-button-text  (i18n/label :t/see-it-again)
                          :on-accept           #(re-frame/dispatch [:hardwallet.ui/recovery-phrase-confirm-pressed])
                          :on-cancel           #(re-frame/dispatch [:hardwallet.ui/recovery-phrase-cancel-pressed])}})

(defn- recovery-phrase-next-word [db]
  {:db (-> db
           (assoc-in [:hardwallet :recovery-phrase :step] :word2)
           (assoc-in [:hardwallet :recovery-phrase :confirm-error] nil)
           (assoc-in [:hardwallet :recovery-phrase :input-word] nil)
           (assoc-in [:hardwallet :setup-step] :recovery-phrase-confirm-word2))})

(fx/defn recovery-phrase-confirm-word
  [{:keys [db]}]
  (let [step (get-in db [:hardwallet :recovery-phrase :step])
        input-word (get-in db [:hardwallet :recovery-phrase :input-word])
        {:keys [word]} (get-in db [:hardwallet :recovery-phrase step])]
    (if (= word input-word)
      (if (= step :word1)
        (recovery-phrase-next-word db)
        (show-recover-confirmation))
      {:db (assoc-in db [:hardwallet :recovery-phrase :confirm-error] (i18n/label :t/wrong-word))})))

(fx/defn generate-and-load-key
  [{:keys [db] :as cofx}]
  (let [{:keys [mnemonic pairing pin]} (get-in db [:hardwallet :secrets])]
    (fx/merge cofx
              {:hardwallet/generate-and-load-key {:mnemonic mnemonic
                                                  :pairing  pairing
                                                  :pin      pin}})))

(fx/defn create-keycard-account
  [{:keys [db] :as cofx}]
  (let [{{:keys [whisper-public-key
                 wallet-address
                 encryption-public-key
                 keycard-instance-uid]} :hardwallet} db]
    (fx/merge (-> cofx
                  (accounts.create/get-signing-phrase)
                  (accounts.create/get-status))
              {:db (assoc-in db [:hardwallet :setup-step] nil)}
              (accounts.create/on-account-created {:pubkey               whisper-public-key
                                                   :address              wallet-address
                                                   :mnemonic             ""
                                                   :keycard-instance-uid keycard-instance-uid}
                                                  encryption-public-key
                                                  {:seed-backed-up? true
                                                   :login?          false})
              (navigation/navigate-to-cofx :hardwallet-success nil))))

(fx/defn on-generate-and-load-key-success
  [{:keys [db random-guid-generator] :as cofx} data]
  (let [{:keys [whisper-public-key
                whisper-private-key
                whisper-address
                wallet-address
                encryption-public-key]} (js->clj data :keywordize-keys true)
        whisper-public-key' (str "0x" whisper-public-key)
        keycard-instance-uid (get-in db [:hardwallet :application-info :instance-uid])]
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:hardwallet :whisper-public-key] whisper-public-key')
                       (assoc-in [:hardwallet :whisper-private-key] whisper-private-key)
                       (assoc-in [:hardwallet :whisper-address] whisper-address)
                       (assoc-in [:hardwallet :wallet-address] wallet-address)
                       (assoc-in [:hardwallet :encryption-public-key] encryption-public-key)
                       (assoc-in [:hardwallet :keycard-instance-uid] keycard-instance-uid)
                       (assoc :node/on-ready :create-keycard-account)
                       (assoc :accounts/new-installation-id (random-guid-generator))
                       (update-in [:hardwallet :secrets] dissoc :mnemonic))}
              (node/initialize nil))))

(fx/defn on-generate-and-load-key-error
  [{:keys [db] :as cofx} {:keys [error code]}]
  (log/debug "[hardwallet] generate and load key error: " error)
  (fx/merge cofx
            {:db (-> db
                     (assoc-in [:hardwallet :return-to-step] :recovery-phrase)
                     (assoc-in [:hardwallet :setup-error] error))}
            (process-error code)))
