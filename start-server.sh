#!/bin/sh -e

lein with-profile production run -m qcast.server
