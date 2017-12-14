(ns mcp342x.core

  ""

  {:author "Adam Helinski"})




;;;;;;;;;; Misc


(defn address

  ""

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




;;;;;;;;;; Configuration byte


(def flags

  ""

  {:ready?  {true  0x80
             false 0x00}
   :channel {1 0x00
             2 0x20
             3 0x40
             4 0x60}
   :mode    {:continuous 0x10
             :one-shot   0x00}
   :bits    {12 0x00
             14 0x04
             16 0x08
             18 0x0c}
   :pga     {1 0x00
             2 0x01
             4 0x02
             8 0x03}})




(defn flag

  ""

  [parameter value]

  (or (get-in flags
              [parameter value])
      (throw (IllegalArgumentException. (format "Bit flag not found for parameter '%s' with value '%s'."
                                                parameter
                                                value)))))




(defn from-config

  ""

  [b]

  {:ready?  (bit-test b
                      7)
   :channel (if (bit-test b
                          5)
              (if (bit-test b
                            6)
                4
                2)
              (if (bit-test b
                            6)
                3
                1))
   :mode    (if (bit-test b
                          4)
              :continuous
              :one-shot)
   :bits    (if (bit-test b
                          2)
              (if (bit-test b
                            3)
                18
                14)
              (if (bit-test b
                            3)
                16
                12))
   :pga     (if (bit-test b
                          0)
              (if (bit-test b
                            1)
                8
                2)
              (if (bit-test b
                            1)
                4
                1))})




(defn to-config

  ""

  ([]

   0x90)


  ([{:as   config
     :keys [ready?
            channel
            mode
            bits
            pga]
     :or   {ready?  true 
            channel 1
            mode    :continuous
            bits    12
            pga     1}}]
 
   (bit-or (flag :ready?
                 ready?)
           (flag :channels
                 channel)
           (flag :mode
                 mode)
           (flag :bits
                 bits)
           (flag :pga
                 pga))))




;;;;;;;;;; Output code


(defn- -ubyte

  ""

  ([ba i]

   (-ubyte ba
           i
           0xff))


  ([^bytes ba i mask]

   (bit-and mask
            (aget ba
                  i)))


  ([^bytes ba i mask shift-left]

   (bit-shift-left (-ubyte ba
                           i
                           mask)
                   shift-left)))




(defn- -sign

  ""

  [i b]

  (if (bit-test b
                i)
    (- b)
    b))




(defn- -output-code-2bytes

  ""

  [mask msb ^bytes ba]

  (-sign msb
         (bit-or (-ubyte ba
                         1)
                 (-ubyte ba
                         0
                         mask
                         8))))




(defn output-code-12bits

  ""

  [^bytes ba]

  (-output-code-2bytes 0x0f
                       11
                       ba))




(defn output-code-14bits 

  ""

  [^bytes ba]

  (-output-code-2bytes 0x3f
                       13
                       ba))




(defn output-code-16bits

  ""

  [^bytes ba]

  (-output-code-2bytes 0xff
                       15
                       ba))




(defn output-code-18bits

  ""

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




(defn output-code

  ""

  [^long bits ba]

  ((case bits
     12 output-code-12bits
     14 output-code-14bits
     16 output-code-16bits
     18 output-code-18bits) ba))




;;;;;;;;;; Read data


(defn input-voltage

  ""

  [^long bits ^long pga ^long output-code]

  (let [lsb (case bits
              12 1000
              14  250
              16   62.5
              18   15.625)]
    (/ (* output-code
          lsb)
       pga)))




(defn from-read

  ""

  [^bytes ba]

  (let [{:as   config
         :keys [bits
                pga]} (from-config (aget ba
                                         2))]
    (assoc config
           :micro-volt
           (input-voltage bits
                          pga
                          (output-code bits
                                       ba)))))



;;;;;;;;;;


;; TODO general call ? Cf. Any datasheet 5.4
