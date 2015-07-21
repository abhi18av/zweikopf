(ns zweikopf.multi
  (:require [clojure.string :as str])
  (:import (org.jruby.embed ScriptingContainer
                            LocalContextScope)
           (org.jruby Ruby
                      RubyArray
                      RubyBasicObject
                      RubyHash
                      RubyHash$RubyHashEntry
                      RubyObject
                      RubyRational
                      RubyString
                      RubyStruct
                      RubySymbol)))

(defprotocol Clojurize
  (clojurize [this ^ScriptingContainer ruby]))

(defprotocol Rubyize
  (rubyize [this ^ScriptingContainer ruby]))

(defn- ^Ruby runtime
  [^ScriptingContainer container]
  (-> container .getProvider .getRuntime))

(defn ruby-eval
  [^ScriptingContainer ruby script]
  (.runScriptlet ruby script))

(defn call-ruby
  [^ScriptingContainer container klass method & args]
  (let [method-name (name method)
        klass (if (string? klass)
                (ruby-eval container (str/replace klass "/" "::"))
                klass)]
    (if (empty? args)
      (.callMethod container klass method-name Object)
      (.callMethod container klass method-name (object-array args) Object))))

(defn context
  []
  (ScriptingContainer. LocalContextScope/SINGLETHREAD))

(defn terminate
  [^ScriptingContainer ctx]
  (.terminate ctx))

(extend-protocol Clojurize
  nil
  (clojurize [this _]
    nil)

  RubySymbol
  (clojurize [this _]
    (clojure.lang.Keyword/intern (.toString this)))

  RubyStruct
  (clojurize [this ruby]
    (let [context (.getCurrentContext (runtime ruby))
          null-block org.jruby.runtime.Block/NULL_BLOCK]
      (persistent!
        (reduce (fn [acc [key val]]
                  (assoc! acc
                          (keyword (clojurize key ruby))
                          (clojurize val ruby)))
                (transient {})
                (call-ruby ruby (.each_pair this context null-block) :to_a)))))

  RubyHash
  (clojurize [this ruby]
    (persistent!
      (reduce (fn [acc ^RubyHash$RubyHashEntry entry]
                (assoc! acc
                        (clojurize (.getKey entry) ruby)
                        (clojurize (.getValue entry) ruby)))
              (transient {})
              (.directEntrySet this))))

  RubyArray
  (clojurize [this ruby]
    (mapv #(clojurize % ruby) this))

  RubyString
  (clojurize [this _]
    (.decodeString this))

  org.jruby.RubyNil
  (clojurize  [_ _]
    nil)

  org.jruby.RubyRational
  (clojurize [this ruby]
    (let [context (.getCurrentContext (runtime ruby))
          numerator (clojurize (.numerator this context) ruby)
          denominator (clojurize (.denominator this context) ruby)]
      (/ numerator denominator)))

  org.jruby.RubyFixnum
  (clojurize  [this _]
    (.getLongValue this))

  org.jruby.RubyFloat
  (clojurize  [this _]
    (.getDoubleValue this))

  org.jruby.RubyBoolean
  (clojurize  [this _]
    (.isTrue this))

  org.jruby.RubyTime
  (clojurize [this _]
    (.toJava this java.util.Date))

  org.jruby.RubyObject
  (clojurize [this ruby]
    (condp #(call-ruby ruby %2 :respond_to? %1) this
      "to_hash"   (clojurize (call-ruby ruby this :to_hash) ruby)
      "strftime"  (-> (call-ruby ruby this :strftime "%s")
                      Long/parseLong
                      (* 1000)
                      java.util.Date.)))

  java.lang.Object
  (clojurize [this _]
    this))

(defn- apply-to-keys-and-values [m f]
  (into {} (for [[k v] m]
             [(f k) (f v)])))

(extend-protocol Rubyize
  clojure.lang.IPersistentMap
  (rubyize [this ruby]
    (doto (RubyHash. (runtime ruby))
      (.putAll (apply-to-keys-and-values this #(rubyize % ruby)))))

  clojure.lang.Ratio
  (rubyize [this ruby]
    (RubyRational/newRational (runtime ruby)
                              (.numerator this)
                              (.denominator this)))

  clojure.lang.Seqable
  (rubyize [this ruby]
    (doto (RubyArray/newArray (runtime ruby))
      (.addAll (for [item this] (rubyize item ruby)))))

  clojure.lang.Keyword
  (rubyize [this ruby]
    (.newSymbol (runtime ruby) (name this)))

  java.lang.Object
  (rubyize [this _]
    this)

  nil
  (rubyize [_ _]
    nil))
