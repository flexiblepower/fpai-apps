#!/bin/bash

echo {flexiblepower.*,net.*} | xargs -n 1 cp flexiblepower.demo.scenario/.checkstyle 2> /dev/null
echo {flexiblepower.*/.settings/,net.*/.settings/} | xargs -n 1 cp flexiblepower.demo.scenario/.settings/* 2> /dev/null
