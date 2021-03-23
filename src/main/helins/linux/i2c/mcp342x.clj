;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.



(ns helins.linux.i2c.mcp342x

  "Library for talking to MCP342x converters using I2C.
  
   Those devices are configured using a single byte and the input voltage is read directly.
  
   Parameters used throughout this library are :

     :mcp342x/channel
       Up to 4 channels, from 1 to 4, are available depending on the model.

     :mcp342x/converting?
       When writing the configuration byte, this parameter must be set to true for initiating
       a new measure in :one-shot mode. It does not matter in :continuous mode.
       When reading the input voltage, the converter sets this parameter to false when a new
       conversion is ready.

     :mcp342x/mode
       In :continuous mode, the converter measures the input voltage constantly whereas in
       :one-shot mode, the measure only happens when the master writes a configuration byte
       with :mcp342x/converting? set to true.

     :mcp342x/pga
       Programamble Gain Amplifier.
       #{:x1 :x2 :x4 :x8}

     :mcp342x/resolution
       Number of bits the input voltage is represented by, depending on the model.
       #{:12-bit :14-bit :16-bit :18-bit}
  

   IO operations will throw in case of failure and are performed using this library :
  
     https://github.com/helins/linux.i2c.clj"

  {:author "Adam Helinski"}

  (:require [helins.linux.i2c :as i2c]))


;;;;;;;;;; Misc


(def default+

  "Default values for this namespace."

  {:mcp342x/channel     1
   :mcp342x/converting? true
   :mcp342x/mode        :continuous
   :mcp342x/pga         :x1
   :mcp342x/resolution  :12-bit})


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

  {:mcp342x/channel     {1 0x00
                         2 0x20
                         3 0x40
                         4 0x60}
   :mcp342x/converting? {true  0x80
                         false 0x00}
   :mcp342x/mode        {:continuous 0x10
                        :one-shot   0x00}
   :mcp342x/pga         {:x1 0x00
                         :x2 0x01
                         :x4 0x02
                         :x8 0x03}
   :mcp342x/resolution  {:12-bits 0x00
                         :14-bits 0x04
                         :16-bits 0x08
                         :18-bits 0x0c}})




(defn- -param+->byte

  ;; Given a map of parameters, returns a configuration byte.
  
  [param+]

  (reduce (fn add-flag [b param]
            (let [value (or (get param+
                                 param)
                            (get default+
                                 param))]
              (bit-or b
                      (or (get-in -flags
                                  [param value])
                          (throw (IllegalArgumentException. (format "Required configuration is invalid : %s %s"
                                                                    param
                                                                    value)))))))
          0
          [:mcp342x/channel
           :mcp342x/converting?
           :mcp342x/mode
           :mcp342x/pga
           :mcp342x/resultion]))



(defn- -byte->param+

  ;; Converts a configuration byte into a map of parameters.

  [b]

  {:mcp342x/channel     (if (bit-test b
                                      5)
                          (if (bit-test b
                                        6)
                            4
                            2)
                          (if (bit-test b
                                        6)
                            3
                            1))
   :mcp342x/converting? (bit-test b
                                  7)
   :mcp342x/mode        (if (bit-test b
                                      4)
                         :continuous
                         :one-shot)
   :mcp342x/pga         (if (bit-test b
                                      0)
                          (if (bit-test b
                                        1)
                            :x8
                            :x2)
                          (if (bit-test b
                                        1)
                            :x4
                            :x1))
   :mcp342x/resolution  (if (bit-test b
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

  [param+ bs]

  (condp identical?
         (:mcp342x/resolution param+)
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

  [param+ bs]

  (let [^double lsb         (condp identical?
                                   (:mcp342x/resolution param+)
                              :12-bits 1000
                              :14-bits  250
                              :16-bits   62.5
                              :18-bits   15.625)
                output-code (-output-code param+
                                          bs)
        ^long   pga         (condp identical?
                                   (:mcp342x/pga param+)
                              :x1 1
                              :x2 2
                              :x4 4
                              :x8 8)]
    (double (/ (* output-code
                  lsb)
               pga))))


;;;;;;;;;; API


(defn address

  "Returns the address of a slave device.
  
   Pins `a0`, `a1` and `a2` are used to modify the default address by specifying a boolean value for each."

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
  
   See [[default+]]."

  [bus param+]

  (i2c/write bus
             [(-param+->byte param+)])
  nil)



(defn read-channel

  "Reads a channel and returns a map containing the parameters under which the measure has been done as well as the
   current input voltage under :mcp342x/micro-volt."

  ([bus]

   (read-channel bus
                 (get default+
                      :mcp342x/resolution)))


  ([bus resolution]

   (let [result (i2c/read bus
                          (condp identical?
                                 resolution
                                 :12-bit 3
                                 :14-bit 3
                                 :16-bit 3
                                 :18-bit 4))
         param+ (-byte->param+ (last result))]
     (assoc param+
            :mcp342x/micro-volt
            (-input-voltage param+
                            result)))))


;;;;;;;;;;


;; TODO general call ? Cf. Any datasheet 5.4
