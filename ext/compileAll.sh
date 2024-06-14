#!/bin/bash

cd adisk; ant $*; cd ..
cd asocket; ant $*; cd ..
cd http; ant $*; cd ..
cd atls; ant $*; cd ..
cd gnutella; ant $*; cd ..