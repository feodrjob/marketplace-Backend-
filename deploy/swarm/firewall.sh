#!/usr/bin/env bash
# One-node Swarm: no cluster traffic is needed on the public interface.
# Keep existing firewall rules, SSH and HTTP/HTTPS untouched.
set -euo pipefail
interface="$(ip -4 route show default | awk '{print $5; exit}')"
[[ "$interface" =~ ^[a-zA-Z0-9_.:-]+$ ]]
for family in iptables ip6tables; do
  command -v "$family" >/dev/null || continue
  for port in 2377 7946; do
    if ! "$family" -w 10 -C INPUT -i "$interface" -p tcp --dport "$port" -j DROP 2>/dev/null; then
      "$family" -w 10 -I INPUT -i "$interface" -p tcp --dport "$port" -j DROP
    fi
  done
  for port in 7946 4789; do
    if ! "$family" -w 10 -C INPUT -i "$interface" -p udp --dport "$port" -j DROP 2>/dev/null; then
      "$family" -w 10 -I INPUT -i "$interface" -p udp --dport "$port" -j DROP
    fi
  done
done
