# MCP342x

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/mcp342x.svg)](https://clojars.org/dvlopt/mcp342x)

Clojure utilities for talking to MCP342x A/D converter via
[I²C](https://en.wikipedia.org/wiki/I%C2%B2C) :

- MCP3421
- MCP3422
- MCP3423
- MCP3424
- MCP3425
- MCP3426
- MCP3427
- MCP3428

This library provides functions for configuring and understanding those chips.

For using I²C itself, from clojure, we recommend
[Icare](https://github.com/dvlopt/icare).

## Usage

Read the full [API](https://dvlopt.github.io/doc/mcp342x.clj/index.html).

Using [Icare](https://github.com/dvlopt/icare) :

```clj
(require '[icare.core :as i2c]
         '[mcp342x    :as adc])


;; first, we need to open the I²C bus we need
(def bus
     (i2c/open "/dev/i2c-1"))


;; then select our ADC slave
(i2c/select bus
            (adc/address))


;; let's configure our ADC in case we don't like the default settings
(i2c/write-byte bus
                (adc/to-config {:channel 2
                                :mode    :continuous
                                :bits    16
                                :pga     4}))


;; now we can read it and interpret the result
(def buffer
     (byte-array 3))


(i2c/read-bytes bus
                buffer)


(adc/from-read buffer)

;; => {:ready?     false
;;     :channel    2
;;     :mode       :continuous
;;     :bits       16
;;     :pga        4
;;     :micro-volt 913000}
```

## License

Copyright © 2017 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
