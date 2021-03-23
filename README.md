# Linux.I2C.MCP342X, driver for A/D

[![Clojars
Project](https://img.shields.io/clojars/v/io.helins/linux.i2c.mcp342x.svg)](https://clojars.org/io.helins/linux.i2c.mcp342x)

[![Cljdoc](https://cljdoc.org/badge/io.helins/linux.i2c.mcp342x)](https://cljdoc.org/d/io.helins/linux.i2c.mcp342x)

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

Relies on [helins/linux.i2c](https://github.com/helins/linux.i2c.clj).


## Usage

This is an overview.

The [detailed API is available on Cljdoc](https://cljdoc.org/d/io.helins/linux.i2c.mcp342x).

In short, without error handling :

```clj
(require '[helins.linux.i2c         :as i2c]
         '[helins.linux.i2c.mcp342x :as mcp342x])


(with-open [bus (i2c/bus "/dev/i2c-1")]

  (i2c/select-slave bus
                    (mcp342x/address true
                                     false
                                     true))

  (mcp342x/configure bus
                     {:mcp342x/channel    2
                      :mcp342x/mode       :continuous
                      :mcp342x/pga        :x1
                      :mcp342x/resolution :16-bit})

  (mcp342x/read-channel bus
                        :16-bit)

  => {:mcp342x/channel    2
      :mcp342x/micro-volt 913000
      :mcp342x/mode       :continuous
      :mcp342x/pga        :x1
      :mcp342x/resolution :16-bit})
```


## License

Copyright © 2017 Adam Helinski

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.
