(ns zweikopf.core-test
  (:import [org.jruby.embed ScriptingContainer])
  (:use clojure.test
        zweikopf.core))

(use-fixtures :once (fn [f]
                      (init-ruby-context)
                      (f)))


(deftest clojurize-test
  (ruby-eval "require 'date'")
  (testing "Numbers"
    (testing "Integer"
      (is (= 123 (clojurize (ruby-eval "123")))))
    (testing "Floating point"
      (is (= 123.45 (clojurize (ruby-eval "123.45")))))
    (testing "Rational"
      (is (= 1/3 (clojurize (ruby-eval "Rational(1,3)")))))
    (testing "Big Decimals"
      (is (= 12.34M (clojurize (ruby-eval "require 'bigdecimal'; BigDecimal.new(12.34,4)"))))))
  (testing "Struct"
    (is (= {:a 1 :b 2} (clojurize (ruby-eval "Struct.new(:a, :b).new(1, 2)")))))
  (testing "Empty hash"
    (is (= {} (clojurize (ruby-eval "{}")))))
  (testing "Non-empty hash"
    (is (= {:a 1 :b 2} (clojurize (ruby-eval "{:a => 1, :b =>2 }")))))
  (testing "Deep hash"
    (is (= {:a 1 :b {:c 3 :d 4}} (clojurize (ruby-eval "{:a => 1, :b => {:c => 3, :d =>4}}")))))
  (testing "Empty array"
    (is (= [] (clojurize (ruby-eval "[]")))))
  (testing "Non-empty array"
    (is (= [1 :a "b"]) (clojurize (ruby-eval "[1, :a, 'b']"))))
  (testing "Deep array"
    (is (= [:a [:c :d] :e :f]) (clojurize (ruby-eval "[:a, [:c, :d], :e, :f]"))))
  (testing "Complex DS"
    (is (= [:a [:c {:d :e}] :f :g] (clojurize (ruby-eval "[:a, [:c, {:d => :e}], :f, :g]")))))
  (testing "Times"
    (testing "DateTime"
      (let [date (clojurize (ruby-eval "DateTime.new(2013,2,19,12,34,56)"))] ;; => Tue, 19 Feb 2013 12:34:56 +0000
        (is (= java.util.Date (class date)))
        (is (= #inst "2013-02-19T12:34:56" date))))))

(deftest rubyize-test
  (testing "Numbers"
    (testing "Integer"
      (is (= (ruby-eval "123")
             (rubyize 123))))
    (testing "Floating point"
      (is (= (ruby-eval "123.45")
             (rubyize 123.45))))
    (testing "Rational"
      (is (= (ruby-eval "Rational(1,3)")
             (rubyize 1/3))))
    (testing "Big Decimals"
      (is (= (ruby-eval "require 'bigdecimal'; BigDecimal.new(12.34,4)")
             (rubyize 12.34M)))))
  (testing "Emtpy hash"
    (is (.equals (ruby-eval "{}") (rubyize {}))))
  (testing "Non-emtpy hash"
    (is (.equals (ruby-eval "{:a => 1, :b => 2}") (rubyize {:a 1 :b 2}))))
  (testing "Deep hash"
    (is (.equals (ruby-eval "{:a => 1, :b => {:c => 3, :d =>4}}") (rubyize {:a 1 :b {:c 3 :d 4}}))))

  (testing "Empty array"
    (is (= (ruby-eval "[]") (rubyize []))))

  (testing "Empty list"
    (is (= (ruby-eval "[]") (rubyize '()))))

  (testing "Non-empty array"
    (is (= (ruby-eval "[1, :a, 'b']") (rubyize [1 :a "b"])) ))

  (testing "Lazy seq"
    (is (= (ruby-eval "[:a, :b, :c]") (rubyize (lazy-seq [:a :b :c])))))

  (testing "Deep array"
    (is (= (ruby-eval "[:a, [:c, :d], :e, :f]") (rubyize [:a [:c :d] :e :f])) ))

  (testing "Complex DS"
    (is (= (ruby-eval "[:a, [:c, {:d => :e}], :f, :g]") (rubyize [:a [:c {:d :e}] :f :g])))))

(deftest performance
  (testing "this spec simply shows performance, very roughly, please do your testing depending on your needs"
    (time
     (let [ruby-obj (ruby-eval "{:a => 1, :b => 2 }")]
       (dotimes [i 100000]
         (clojurize ruby-obj))))
    (time
     (let [clj-obj {:a 1 :b 2}]
       (dotimes [i 100000]
         (rubyize clj-obj))))))
