(ns dvlopt.i2c.mcp342x

  "Specs and functions for talking to MCP342x converters using I2C.
  
   Those devices are configured using a single byte and the input voltage is read directly.
  
   Parameters used throughout this library are :

     ::channel
       Up to 4 channels are available depending on the model.

     ::converting?
       When writing the configuration byte, this parameter must be set to true for initiating
       a new measure in :one-shot mode. It does not matter in :continuous mode.
       When reading the input voltage, the converter sets this parameter to false when a new
       conversion is ready.

     ::mode
       In :continuous mode, the converter measures the input voltage constantly whereas in
       :one-shot mode, the measure only happens when the master writes a configuration byte
       with ::converting? set to true.

     ::pga
       Programamble Gain Amplifier.

     ::resolution
       Number of bits the input voltage is represented by, depending on the model."

  {:author "Adam Helinski"}

  (:require [clojure.spec.alpha :as s]
            [dvlopt.i2c         :as i2c]
            [dvlopt.void        :as void]))




;;;;;;;;;; Specs


(s/def ::active?

  boolean?)


(s/def ::byte

  (s/int-in 0
            256))


(s/def ::channel

  #{1 2 3 4})


(s/def ::converting?

  boolean?)


(s/def ::data

  (s/merge ::parameters
           (s/keys :req [::micro-volt])))


(s/def ::data-buffer

  (s/and bytes?
         #(>= (count %)
              3)))


(s/def ::micro-volt

  (s/double-in :infinite? false
               :NaN?      false))


(s/def ::mode

  #{:continuous
    :one-shot})


(s/def ::output-code

  int?)


(s/def ::parameter

  #{::channel
    ::converting?
    ::mode
    ::pga
    ::resolution})


(s/def ::parameters

  (s/keys :req [::channel
                ::converting?
                ::mode
                ::pga
                ::resolution]))


(s/def ::parameters.opts

  (s/nilable (s/keys :opt [::channel
                           ::converting?
                           ::mode
                           ::pga
                           ::resolution])))


(s/def ::pga

  #{:x1 
    :x2
    :x4
    :x8})


(s/def ::resolution

  #{:12-bits
    :14-bits
    :16-bits
    :18-bits})




;;;;;;;;;; Private - Bit manipulations



(defn- -sign

  "Given a byte, checks the bit at `i` and treats it as a sign bit.
  
   Returns a signed byte accordingly."

  [i b]

  (if (bit-test b
                i)
    (- b)
    b))




(defn- -ubyte

  "Given a byte array, gets an 'unsigned' byte and shifts it to the left if needed."

  ([ba i]

   (-ubyte ba
           i
           0xff))


  ([^bytes ba i mask]

   (if (< i
          (count ba))
     (bit-and mask
              (aget ba
                    i))
     0))


  ([^bytes ba i mask shift-left]

   (bit-shift-left (-ubyte ba
                           i
                           mask)
                   shift-left)))




;;;;;;;;;; API - Defaults


(def defaults

  "Default values for this namespace."

  {::channel     1
   ::converting? true
   ::mode        :continuous
   ::pga         1
   ::resolution  12})




;;;;;;;;;; API - Misc


(s/fdef address

  :args (s/? (s/cat :a0 ::active?
                    :a1 ::active?
                    :a2 ::active?))
  :ret  ::i2c/address)


(defn address

  "Gets the address of a slave device.
  
   The user can use pins `a0`, `a1` and `a2` to modify the default address."

  ([]

   0x0d)


  ([a0 a1 a2]

   (bit-or (address)
           (if a0
             0x01
             0x00)
           (if a1
             0x02
             0x00)
           (if a2
             0x04
             0x00))))




;;;;;;;;;; API - Configuration


(def flags

  "Bit flags for the configuration byte."

  {::channel     {1 0x00
                  2 0x20
                  3 0x40
                  4 0x60}
   ::converting? {true  0x80
                  false 0x00}
   ::mode        {:continuous 0x10
                  :one-shot   0x00}
   ::pga         {:x1 0x00
                  :x2 0x01
                  :x4 0x02
                  :x8 0x03}
   ::resolution  {:12-bits 0x00
                  :14-bits 0x04
                  :16-bits 0x08
                  :18-bits 0x0c}})




(s/fdef byte->parameters

  :args (s/cat :b ::byte)
  :ret  ::parameters)


(defn byte->parameters

  "Converts a configuration byte into a map of parameters."

  [b]

  {::channel     (if (bit-test b
                               5)
                   (if (bit-test b
                                 6)
                     4
                     2)
                   (if (bit-test b
                                 6)
                     3
                     1))
   ::converting? (bit-test b
                           7)
   ::mode        (if (bit-test b
                               4)
                  :continuous
                  :one-shot)
   ::pga         (if (bit-test b
                               0)
                   (if (bit-test b
                                 1)
                     :x8
                     :x2)
                   (if (bit-test b
                                 1)
                     :x4
                     :x1))
   ::resolution  (if (bit-test b
                               2)
                   (if (bit-test b
                                 3)
                     :18-bits
                     :14-bits)
                   (if (bit-test b
                                 3)
                     :16-bits
                     :12-bits))})




(s/fdef get-flag

  :args (s/cat :opts      ::parameters.opts
               :parameter ::parameter)
  :ret  ::byte)


(defn get-flag

  "Gets the flag value of a parameter, fetching the default one if needed."

  [opts parameter]

  (or (get (get flags
                parameter)
           (void/obtain parameter
                        opts
                        defaults))
      0))




(s/fdef parameters->byte

  :args (s/cat :opts (s/? ::parameters.opts))
  :ret  ::byte)


(defn parameters->byte

  "Given a map of parameters, returns a configuration byte."
  
  ([]

   (parameters->byte nil))


  ([opts]

   (bit-or (get-flag ::channel
                     opts)
           (get-flag ::mode
                     opts)
           (get-flag ::pga
                     opts)
           (get-flag ::ready?
                     opts)
           (get-flag ::resolution
                     opts))))




;;;;;;;;;; Output code


(defn- -output-code

  "Helper for combining 2 bytes into an ouput code which can then be used to
   compute the input voltage."

  [mask msb ^bytes ba]

  (-sign msb
         (bit-or (-ubyte ba
                         1)
                 (-ubyte ba
                         0
                         mask
                         8))))




(defn- -output-code--12bits

  "Given 2 bytes, computes the output code in 12 bits mode.
  
   Takes care of the sign."

  [^bytes ba]

  (-output-code 0x0f
                11
                ba))




(defn- -output-code--14bits 

  "Given 2 bytes, computes the output code in 14 bits mode.
  
   Takes care of the sign."

  [^bytes ba]

  (-output-code 0x3f
                13
                ba))




(defn- -output-code--16bits

  "Given 2 bytes, computes the output code in 16 bits mode.
  
   Takes care of the sign."

  [^bytes ba]

  (-output-code 0xff
                15
                ba))




(defn- -output-code--18bits

  "Given 3 bytes, computes the output code in 18 bits mode.
  
   Takes care of the sign."

  [^bytes ba]

  (-sign 17
         (bit-or (-ubyte ba
                         2)
                 (-ubyte ba
                         1
                         0xff
                         8)
                 (-ubyte ba
                         0
                         0x03
                         16))))




(s/fdef output-code

  :args (s/cat :resolution  ::resolution
               :data-buffer ::data-buffer)
  :ret  ::output-code)


(defn output-code

  "Given the resolution, computes the output code of a data buffer."

  [resolution data-buffer]

  ((condp identical?
          resolution
     :12-bits -output-code--12bits
     :14-bits -output-code--14bits
     :16-bits -output-code--16bits
     :18-bits -output-code--18bits) data-buffer))




;;;;;;;;;; API - Read data


(s/fdef data-buffer

  :args (s/cat :resolution ::resolution)
  :ret  ::data-buffer)


(defn data-buffer

  "Creates a byte array depending on the needed resolution for reading data."

  [resolution]

  (byte-array (condp identical?
                     resolution
                :12-bits 3
                :14-bits 3
                :16-bits 3
                :18-bits 4)))




(s/fdef input-voltage

  :args (s/cat :resolution  ::resolution
               :pga         ::pga
               :output-code ::output-code)
  :ret  ::micro-volt)


(defn input-voltage

  "Given the resolution, the PGA and the output code, computes the input voltage."

  ^double

  [resolution pga ^double output-code]

  (let [^double lsb  (condp identical?
                            resolution
                       :12-bits 1000
                       :14-bits  250
                       :16-bits   62.5
                       :18-bits   15.625)
        ^long   pga' (condp identical?
                            pga
                       :x1 1
                       :x2 2
                       :x4 4
                       :x8 8)]
    (double (/ (* output-code
                  lsb)
               pga'))))




(s/fdef process

  :args (s/cat :ba ::data-buffer)
  :ret  ::data)


(defn process

  "Process a data buffer in order to read the parameters and input voltage."

  [data-buffer]

  (let [parameters (byte->parameters (or (last data-buffer)
                                          0))
        resolution (::resolution parameters)]
    (assoc parameters
           ::micro-volt
           (input-voltage resolution
                          (::pga parameters)
                          (output-code resolution
                                       data-buffer)))))




;;;;;;;;;;


;; TODO general call ? Cf. Any datasheet 5.4
