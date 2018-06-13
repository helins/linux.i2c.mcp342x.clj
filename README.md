# linux.i2c.mcp342x

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/linux.i2c.mcp342x.svg)](https://clojars.org/dvlopt/linux.i2c.mcp342x)

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

Relies on [dvlopt/linux.i2c](https://github.com/dvlopt/linux.i2c.clj).

## Usage

Read the
[API](https://dvlopt.github.io/doc/clojure/dvlopt/linux.i2c.mcp342x/index.html).

In short, without erorr handling :

```clj
(require '[dvlopt.linux.i2c         :as i2c]
         '[dvlopt.linux.i2c.mcp342x :as adc])


(with-open [^java.lang.AutoCloseable bus (i2c/bus "/dev/i2c-1")]

  (i2c/select-slave bus
                    (adc/address true
                                 false
                                 true))

  (adc/configure bus
                 {::adc/channel    2
                  ::adc/mode       :continuous
                  ::adc/pga        :x1
                  ::adc/resolution :16-bit})

  (adc/read-channel bus
                    :16-bit)

  => {::adc/channel    2
      ::adc/micro-volt 913000
      ::adc/mode       :continuous
      ::adc/pga        :x1
      ::adc/resolution :16-bit}

  )
```

## License

Copyright © 2017 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
