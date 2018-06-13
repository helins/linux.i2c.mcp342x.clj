(ns dvlopt.linux.i2c.mcp342x

  "Library for talking to MCP342x converters using I2C.
  
   Those devices are configured using a single byte and the input voltage is read directly.
  
   Parameters used throughout this library are :

     ::channel
       Up to 4 channels, from 1 to 4, are available depending on the model.

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
       #{:x1 :x2 :x4 :x8}

     ::resolution
       Number of bits the input voltage is represented by, depending on the model.
       #{:12-bit :14-bit :16-bit :18-bit}
  

   IO operations will throw in case of failure and are performed using this library :
  
     https://github.com/dvlopt/linux.i2c.clj"

  {:author "Adam Helinski"}

  (:require [dvlopt.linux.i2c :as i2c]
            [dvlopt.void      :as void]))




;;;;;;;;;; Misc


(def defaults

  "Default values for this namespace."

  {::channel     1
   ::converting? true
   ::mode        :continuous
   ::pga         :x1
   ::resolution  :12-bit})




;;;;;;;;;; Bit manipulations


(defn- -sign

  ;; Given a byte, checks the bit at `i` and treats it as a sign bit.
  ;;
  ;; Returns a signed byte accordingly.

  [i b]

  (if (bit-test b
                i)
    (- b)
    b))




(defn- -ubyte

  ;; Given a sequence of bytes, gets an 'unsigned' byte and shifts it to the left if needed.

  ([bs i mask]

   (bit-and (nth bs
                 i)
            mask))

  ([bs i mask shift-left]

   (bit-shift-left (-ubyte bs
                           i
                           mask)
                   shift-left)))




;;;;;;;;;; Handling parameters


(def ^:private -flags

  ;; Bit flags for the configuration byte.

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




(defn- -parameters->byte

  ;; Given a map of parameters, returns a configuration byte.
  
  [parameters]

  (reduce (fn add-flag [b parameter]
            (let [value (void/obtain parameter
                                     parameters
                                     defaults)]
              (bit-or b
                      (or (get-in -flags
                                  [parameter value])
                          (throw (IllegalArgumentException. (format "Required configuration is invalid : %s %s"
                                                                    parameter
                                                                    value)))))))
          0
          [::channel
           ::converting?
           ::mode
           ::pga
           ::resultion]))




(defn- -byte->parameters

  ;; Converts a configuration byte into a map of parameters.

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
                     :18-bit
                     :14-bit)
                   (if (bit-test b
                                 3)
                     :16-bit
                     :12-bit))})




;;;;;;;;;; Computing output codes and input voltages


(defn- -compute-output-code

  ;; Helper for combining 2 bytes into an ouput code which can then be used to
  ;; compute the input voltage.

  [bs msb mask]

  (-sign msb
         (bit-or (get bs
                      1)
                 (-ubyte bs
                         0
                         mask
                         8))))




(defn- -output-code

  ;; Computes the output-code taking into account the resolution.

  [parameters bs]

  (condp identical?
         (::resolution parameters)
    :12-bit (-compute-output-code bs
                                  11
                                  0x0f)
    :14-bit (-compute-output-code bs
                                  13
                                  0x3f)
    :16-bit (-compute-output-code bs
                                  15
                                  0xff)
    :18-bit (-sign 17
                   (bit-or (nth bs
                                2)
                           (-ubyte bs
                                   1
                                   0xff
                                   8)
                           (-ubyte bs
                                   0
                                   0x03
                                   16)))))




(defn- -input-voltage

  ;; Given the resolution, the PGA and the output code, computes the input voltage."

  ^double

  [parameters bs]

  (let [^double lsb         (condp identical?
                                   (::resolution parameters)
                              :12-bits 1000
                              :14-bits  250
                              :16-bits   62.5
                              :18-bits   15.625)
                output-code (-output-code parameters
                                          bs)
        ^long   pga         (condp identical?
                                   (::pga parameters)
                              :x1 1
                              :x2 2
                              :x4 4
                              :x8 8)]
    (double (/ (* output-code
                  lsb)
               pga))))




;;;;;;;;;; API


(defn address

  "Gets the address of a slave device.
  
   The user can use pins `a0`, `a1` and `a2` to modify the default address by specifying a boolean value for each."

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




(defn configure

  "Configures the slave device by providing parameters.

   Do not forget to select the slave device first.
  
   In absence of a parameter, a default value will be used.
  
   Cf. `defaults`"

  [bus parameters]

  (i2c/write bus
             [(-parameters->byte parameters)])
  nil)




(defn read-channel

  "Reads a channel and returns a map containing the parameters under which the measure has been done as well as the
   current input voltage under ::micro-volt."

  ([bus]

   (read-channel bus
                 (get defaults
                      ::resolution)))


  ([bus resolution]

   (let [result     (i2c/read bus
                              (condp identical?
                                     resolution
                                     :12-bit 3
                                     :14-bit 3
                                     :16-bit 3
                                     :18-bit 4))
         parameters (-byte->parameters (last result))]
     (assoc parameters
            ::micro-volt
            (-input-voltage parameters
                            result)))))




;;;;;;;;;;


;; TODO general call ? Cf. Any datasheet 5.4
