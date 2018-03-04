# dvlopt.mcp342x

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/i2c.mcp342x.svg)](https://clojars.org/dvlopt/i2c.mcp342x)

Clojure library for talking to the MCP342x family of A/D converters using
[I2C](https://en.wikipedia.org/wiki/I%C2%B2C) :

- MCP3421
- MCP3422
- MCP3423
- MCP3424
- MCP3425
- MCP3426
- MCP3427
- MCP3428


For using I2C itself from clojure we recommend
[dvlopt.i2c](https://github.com/dvlopt/i2c).

## Usage

Read the [API](https://dvlopt.github.io/doc/dvlopt/i2c.mcp342x/).

Using [dvlopt.i2c](https://github.com/dvlopt/i2c) (without error checking) :

```clj
(require '[dvlopt.i2c         :as i2c]
         '[dvlopt.i2c.mcp342x :as adc])


;; First, we need to open the I2C bus we need
(def bus
     (::i2c/bus (i2c/open "/dev/i2c-1")))


;; Then we select our ADC slave device
(i2c/select bus
            (adc/address))


;; Let's configure our ADC in case we don't like the default settings
(i2c/write-byte bus
                (adc/parameters->byte {::adc/channel    2
                                       ::adc/mode       :continuous
                                       ::adc/pga        :x1
                                       ::adc/resolution :16-bits}))


;; Now we can read the converter and process the data
(def buff
     (adc/data-buffer :16-bits))


(i2c/read-bytes bus
                buff)


(adc/process buff)

;; => {::adc/channel     2
;;     ::adc/converting? false
;;     ::adc/mode        :continuous
;;     ::adc/micro-volt  913000
;;     ::adc/pga         1
;;     ::adc/resolution  16}
```

## License

Copyright © 2017-2018 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
