#!/usr/bin/env bash
# Sets up ONE WireGuard exit node on Ubuntu/Debian. Run as root.
# Usage: IFACE=wg0 SUBNET=10.8.0.0/24 PORT=51820 ./setup_node.sh
set -euo pipefail
[ "$EUID" -eq 0 ] || { echo "run as root"; exit 1; }
IFACE=${IFACE:-wg0}; SUBNET=${SUBNET:-10.8.0.0/24}; PORT=${PORT:-51820}
GW=$(python3 -c "import ipaddress,sys;n=ipaddress.ip_network('$SUBNET');print(next(n.hosts()))")
PREFIX=${SUBNET#*/}
WAN=$(ip -4 route show default | awk '{print $5; exit}')

apt-get update && apt-get install -y wireguard unbound iptables
umask 077
install -d /etc/wireguard
[ -f /etc/wireguard/server.key ] || wg genkey > /etc/wireguard/server.key
wg pubkey < /etc/wireguard/server.key > /etc/wireguard/server.pub

cat > /etc/wireguard/$IFACE.conf <<CONF
[Interface]
Address = $GW/$PREFIX
ListenPort = $PORT
PrivateKey = $(cat /etc/wireguard/server.key)
# Peers are added at runtime by the provisioning API (wg set ...).
PostUp   = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -j ACCEPT; iptables -t nat -A POSTROUTING -s $SUBNET -o $WAN -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -j ACCEPT; iptables -t nat -D POSTROUTING -s $SUBNET -o $WAN -j MASQUERADE
CONF

echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/99-freevpn.conf
sysctl --system >/dev/null

# DNS resolver reachable only from the tunnel subnet
cat > /etc/unbound/unbound.conf.d/freevpn.conf <<CONF
server:
  interface: $GW
  ip-freebind: yes
  access-control: $SUBNET allow
  hide-identity: yes
  hide-version: yes
  qname-minimisation: yes
CONF

systemctl enable --now wg-quick@$IFACE
systemctl restart unbound
echo; echo "Node ready. Open UDP $PORT in your firewall/cloud security group."
echo "Server public key (put in config.json 'publicKey'):"; cat /etc/wireguard/server.pub
