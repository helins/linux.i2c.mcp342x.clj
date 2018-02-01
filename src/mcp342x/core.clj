(ns mcp342x.core

  "Utilities for understanding the I2C protocol for the MCP342x family of A/D."

  {:author "Adam Helinski"})




;;;;;;;;;; Misc


(defn address

  "Gets the address of a slave.
  
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




;;;;;;;;;; Configuration byte


(def config-flags

  "Bit flags for the configuration byte.
  
   Cf. `config-flag`"

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




(defn config-flag

  "Given a parameter and a value, gets the related bit flag.
  
   Throws an IllegalArgumentException when not found.
  
  
   Cf. `config-flags` for parameters and values."

  [parameter value]

  (or (get-in config-flags
              [parameter value])
      (throw (IllegalArgumentException. (format "Bit flag not found for parameter '%s' with value '%s'."
                                                parameter
                                                value)))))




(defn from-config

  "Given a configuration byte, returns an understandable map.

     {:ready?
       When true,  the newest value has not been read because it is not ready yet.
            false, the newest value has been read.
       Might be a bit counter-intuitive, but we keep the original semantics.

      :channel
       Selected channel.
  
      :mode
       Either :continuous, meaning it is constantly measuring.
              :one-shot,   meaning it only measure when the master writes a configuration byte
                           with `:ready` set to true or any other bit is different from the last
                           time.

      :bits
       Selected precision, one of #{12 14 16 18} bits.

      :pga
       Selected Programmable Gain Amplifier, one of #{1 2 4 8}.}
  

  Cf. `config-flags`"

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

  "Given a configuration map, returns a configuration byte.
  
     {:ready? 
       In :continuous mode, doesn't do anything.
          :one-shot   mode, when 'true', signals the slave it should measure once.

      :channel
       Which channel select.

      :mode
       Either :continuous for measuring constantly.
              :one-shot   for measuring only once when asked to.

      :bits
       Precision, one of #{12 14 16 18} bits.

      :pga
       Programmable Gain Amplifier, one of #{1 2 4 8}.}
  
  
   Cf. `config-flags`"
  
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
 
   (bit-or (config-flag :ready?
                        ready?)
           (config-flag :channel
                        channel)
           (config-flag :mode
                        mode)
           (config-flag :bits
                        bits)
           (config-flag :pga
                        pga))))




;;;;;;;;;; Output code


(defn- -ubyte

  "Given a byte array, gets an 'unsigned' byte and shifts it to the left if needed."

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

  "Given a byte, checks the bit at `i` and treats it as a sign bit.
  
   Returns a signed byte accordingly."

  [i b]

  (if (bit-test b
                i)
    (- b)
    b))




(defn- -output-code-2bytes

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




(defn output-code-12bits

  "Given 2 bytes, computes the output code in 12 bits mode.
  
   Takes care of the sign."

  [^bytes ba]

  (-output-code-2bytes 0x0f
                       11
                       ba))




(defn output-code-14bits 

  "Given 2 bytes, computes the output code in 14 bits mode.
  
   Takes care of the sign."

  [^bytes ba]

  (-output-code-2bytes 0x3f
                       13
                       ba))




(defn output-code-16bits

  "Given 2 bytes, computes the output code in 16 bits mode.
  
   Takes care of the sign."

  [^bytes ba]

  (-output-code-2bytes 0xff
                       15
                       ba))




(defn output-code-18bits

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




(defn output-code

  "Given a bit mode (12, 14, 16 or 18) and a byte array (size 3 for 18 bits, size 2 otherwise),
   computes the output code which can then be converted to the input voltage."

  [^long bits ba]

  ((case bits
     12 output-code-12bits
     14 output-code-14bits
     16 output-code-16bits
     18 output-code-18bits) ba))




;;;;;;;;;; Read data


(defn input-voltage

  "Given a bit mode (12, 14, 16 or 18), the PGA (1, 2, 4 or 8) and an output code,
   computes the input voltage."

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

  "Given bytes read from the slave, returns a map containing the configuration and the input voltage
   expressed in µV.
  

   In 12, 14 or 16 bits mode -> 3 bytes (2 data bytes + the configuration byte).
  
   In 18 bits mode           -> 4 bytes (3 data bytes + the configuration byte)."

  [^bytes ba]

  (let [{:as   config
         :keys [bits
                pga]} (from-config (last ba))]
    (assoc config
           :micro-volt
           (input-voltage bits
                          pga
                          (output-code bits
                                       ba)))))



;;;;;;;;;;


;; TODO general call ? Cf. Any datasheet 5.4
