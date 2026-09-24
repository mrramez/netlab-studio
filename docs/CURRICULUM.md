# Curriculum Map, Errata and Golden Test Vectors

Source course: "Computer Networks / Networks I" (2nd year) — lectures + lab manual (10 labs).
The course files are NOT part of this repo (copyright). This file is original work summarising
**which topics** must be covered, **what the source gets wrong**, and **exact answers** for tests.

## 1. Lesson list (v1)

| # | Lesson | Must cover | Interactive widget idea |
|---|---|---|---|
| 1 | Data communication basics | 5 components (message, sender, receiver, medium, protocol); simplex / half-duplex / full-duplex with examples; performance (transit time, response time, throughput), reliability, security; point-to-point vs multipoint | Animated data-flow modes; "pick the mode" game (keyboard, walkie-talkie, phone) |
| 2 | Topologies & network types | Bus, ring, star, mesh (full/partial), hybrid; pros/cons; mesh needs n(n−1)/2 links and n−1 ports per device; physical vs logical topology; LAN / MAN / WAN; Internet = network of networks | Build each topology, then click a link/device to break it and watch what fails; link counter |
| 3 | OSI & TCP/IP models | 7 layers + mnemonic "Please Do Not Tell Secret Passwords Anyone"; function of each layer; PDUs (Data, Segment, Packet, Frame, Bits); encapsulation/de-encapsulation; trailer only at L2; process-to-process (L4) vs host-to-host (L3) vs hop-to-hop (L2); MAC changes every hop, IP does not; ISO = organisation, OSI = model; TCP/IP 4-layer and 5-layer views mapped to OSI | Encapsulation animation (headers stack/unstack with layer colours); drag-and-drop "put protocol in its layer" (HTTP, TCP, IP, Ethernet, ...) |
| 4 | Transmission media | Guided vs unguided; UTP vs STP; UTP categories; RJ45; coax + BNC/T/terminator; fiber: core/cladding, total internal reflection, single-mode vs multimode (step/graded index), connectors SC/ST/MT-RJ, pros/cons; radio (3 kHz–1 GHz, omnidirectional), microwave (1–300 GHz, line of sight, unidirectional), infrared (300 GHz–400 THz, short range, no wall penetration); ground/sky/line-of-sight propagation; metric prefixes | **Cable crimping puzzle**: arrange 8 wire colours for T568B straight-through and crossover (other end T568A); choose the right cable for PC–switch, switch–switch, PC–PC, router–PC |
| 5 | Network devices | Passive hub, repeater (regenerator not amplifier), active hub (multiport repeater), bridge (learning, filtering, forwarding, loop problem → spanning tree idea), L2 switch (ASIC, MAC table, per-port collision domain), router (L3, routing table, breaks broadcast domains), L3 switch, gateway, firewall, NIC; collision vs broadcast domains | "Count the domains" exercise on generated topologies; switch MAC-learning step-through |
| 6 | Data Link layer | LLC and MAC sublayers; frame = header + data + trailer; frame fields; L2 address used only on the local link; router de-encapsulates/re-encapsulates at each hop; half/full duplex; CSMA/CD (legacy Ethernet) vs CSMA/CA (802.11); controlled access (token) | CSMA/CD collision + random back-off animation vs CSMA/CA duration reservation |
| 7 | Network layer | IP is connectionless, best effort, media independent; IPv4 header key fields (Version, DS, TTL, Protocol, Header Checksum, Source, Destination); MTU + fragmentation (IPv4 routers fragment, IPv6 routers do not); IPv4 limitations → IPv6 (128-bit, 40-byte fixed header, Traffic Class, Flow Label, Payload Length, Next Header, Hop Limit, extension headers); host forwarding decision (itself / local / remote), default gateway, `route print` | Clickable IPv4 header (hover a field → meaning, size in bits); "local or remote?" decision trainer with AND operation shown in binary |
| 8 | ARP | MAC vs IP roles; destination on same network → dest MAC; remote → gateway MAC; ARP request broadcast, reply unicast; ARP table, aging, `arp -a`, `show ip arp` | Step-through of an ARP exchange inside the simulator |
| 9 | Transport layer | Role; segmentation & multiplexing; TCP features (reliable, ordered, flow control, stateful) and header fields; UDP features and 8-byte header; when to use which (VoIP, DNS, DHCP, TFTP, SNMP vs web/email/FTP); port ranges; well-known ports table; sockets; 3-way handshake (SYN, SYN-ACK, ACK) and 4-step termination (FIN, ACK, FIN, ACK); `netstat` | Handshake / teardown sequence diagram animation; "match the port" game |
| 10 | Application layer | App/Presentation/Session in TCP/IP; client-server vs P2P (BitTorrent...); HTTP steps (URL parts → DNS → GET → HTML render), GET/POST/PUT, HTTPS; SMTP 25, POP3 110 (downloads & deletes), IMAP 143 (keeps on server); DNS (A, AAAA, NS, MX; hierarchy; TLDs; caching; `nslookup`); DHCP (pool, lease, DORA: Discover/Offer/Request/Ack, UDP 67/68); FTP control 21 + data 20 | "What happens when you type a URL" animated story; DORA animation |
| 11 | IP addressing | IPv4 32-bit dotted decimal; binary ↔ decimal (128 64 32 16 8 4 2 1); network ID vs host ID; classes A–E with ranges and default masks; network address (host bits 0), broadcast (host bits 1); loopback 127.0.0.0/8; private ranges 10/8, 172.16/12, 192.168/16; IPv6 notation; MAC address (OUI + serial) | Binary trainer (timed); "network / broadcast / mask" drill |
| 12 | Subnetting, VLSM, summarization | Borrowing bits; subnets = 2^s, hosts = 2^h − 2; block size = 256 − mask octet; CIDR notation; VLSM largest-first; route summarization by common prefix and "256 − number of networks" trick | Subnetting Lab (see SPEC §7) |
| 13 | Cisco IOS basics | Boot (POST, bootloader, IOS, startup-config); access (console, Telnet 23 plaintext, SSH 22 encrypted); modes; hostname; enable password vs enable secret; console/vty passwords; banner motd; interface IP + `no shutdown`; switch SVI + default gateway; duplex/speed/auto-MDIX; running vs startup config (RAM vs NVRAM); `show` commands; SSH configuration steps; loopback interface; interface errors (runts, giants, CRC, late collisions) | CLI Practice with guided mini-tasks |

Phase 2 lessons: DHCP configuration on a router (Lab 8), static & default routing + longest-prefix
match (Lab 9), VLANs + trunks + 802.1Q (Lab 10), traceroute.

## 2. Guided scenarios (from the lab worksheets, rewritten)

| Id | Scenario | Goal checks |
|---|---|---|
| S1 | Two PCs, direct cable (Lab 2) | Correct cable chosen (crossover in strict mode); same subnet; ping succeeds |
| S2 | Star LAN: 4 PCs + switch, 192.168.10.1–4/24 (Lab 3) | All pings succeed; student opens ARP table and answers "which MACs are cached?" |
| S3 | Hub vs switch comparison | Same topology with hub then switch; student observes flooding vs forwarding and counts collision domains |
| S4 | Router joins two LANs, 192.168.1.0/24 and 192.168.2.0/24 (Lab 7) | Interfaces configured + `no shutdown`; PCs have gateways; cross-network ping succeeds. Then "break" variants: missing gateway, interface shut, wrong mask |
| S5 | Three subnets of 192.14.2.0 behind one router (Lab 5 B) | Uses /27 subnets; each PC gets valid host IP + gateway |
| S6 | Router hardening via CLI | hostname, enable secret, console + vty passwords, banner, SSH enabled, config saved |
| S7 (v2) | Exam Q1 topology (two routers, 3 LANs from 10.132.10.0/24) | Addressing table correct; static routes; end-to-end ping |

## 3. Errata — mistakes in the source material (the app must be correct)

| Source says | Correct |
|---|---|
| Registered ports 1024–49151 "are also known as ephemeral ports" | Ephemeral (dynamic/private) ports are **49152–65535**. Registered ports are not ephemeral. |
| UDP Length = "length of the UDP datagram header" | UDP Length = **header + data** in bytes (minimum 8). |
| "Waves 300 GHz to 400 GHz are called infrared" | Infrared ≈ **300 GHz to 400 THz**. |
| CLI "is also known as Cash Line Interface" | **Command-Line Interface**. |
| Data link layer "is responsible for error detection and correction" | Ethernet does error **detection** (FCS/CRC) and discards bad frames; it does not correct them. |
| Full-duplex "allows both devices to transmit and receive simultaneously on a shared medium" | Full-duplex needs a **dedicated (point-to-point) link**, e.g. switch port ↔ one host; no collisions. Shared media are half-duplex. |
| IPv6 header table lists "Source IPv4 Address 128 bit" | Source/Destination **IPv6** Address, 128 bits each. |
| Lab 7 worksheet: Network B router interface labelled "G0/0" | Second LAN must be on **G0/1** (G0/0 is already Network A). |
| Lab 7: PC mask "255.0.0.0.0" | **255.0.0.0** (four octets). |
| Lab 10: "Native VLAN: Truck Traffic" | **Trunk** traffic (untagged frames on an 802.1Q trunk). |
| Lab 8: DHCP means "no chance of conflicts in IP addresses" | DHCP greatly **reduces** conflicts; they can still happen (e.g. static IPs inside the pool). Cisco routers ping-check before leasing. |
| Lab 9: "N routers need N × N routes" | Simplification. Each router needs one route per **non-directly-connected** destination network (or a default route). Present it as a rule of thumb only. |
| Subnet count formulas | Use **2^s** subnets (subnet-zero allowed, modern Cisco default) and **2^h − 2** hosts. Mention the older 2^s − 2 convention in a "Did you know" box. |

When a lesson touches one of these, add a short "Common mistake" box with the correct fact.

## 4. Golden test vectors (use in JUnit tests)

### 4.1 Addressing basics (Lab 4 worksheet)
| Input | Network | Broadcast | Mask |
|---|---|---|---|
| 112.16.254.1/8 | 112.0.0.0 | 112.255.255.255 | 255.0.0.0 |
| 192.16.254.1/24 | 192.16.254.0 | 192.16.254.255 | 255.255.255.0 |
| 172.16.254.1/16 | 172.16.0.0 | 172.16.255.255 | 255.255.0.0 |

Binary: 185.80.72.105 = `10111001.01010000.01001000.01101001`;
39.57.109.234 = `00100111.00111001.01101101.11101010`;
192.168.1.45 = `11000000.10101000.00000001.00101101`;
158.80.164.3 = `10011110.01010000.10100100.00000011`.

### 4.2 Subnetting
- 10.61.219.43 255.240.0.0 (/12, class A): borrowed 4 bits → **16 subnets**, **1,048,574 hosts**/subnet;
  first three subnets 10.0.0.0/12, 10.16.0.0/12, 10.32.0.0/12; the host itself is in **10.48.0.0/12**
  (broadcast 10.63.255.255).
- 192.14.2.0, need 6 subnets → borrow 3 bits → /27 (255.255.255.224), 8 subnets, 30 hosts each;
  first three: 192.14.2.0–.31, 192.14.2.32–.63, 192.14.2.64–.95 (usable .1–.30, .33–.62, .65–.94).
- /29 → 6 usable hosts.
- Exam Q2: host 50.85.45.133 255.192.0.0 → subnet **50.64.0.0/10**, broadcast 50.127.255.255.
- Exam Q3: 100.10.70.0 255.255.240.0 (/20, class A default /8) → **4096 subnets**, **4094 hosts**/subnet
  (the address itself lies in 100.10.64.0/20).
- Exam Q4: 200.90.0.0 255.255.128.0 (/17) → broadcast **200.90.127.255**
  (note: mask shorter than class C default — a supernet; the app should explain this).

### 4.3 VLSM
- 192.168.1.0/24, needs 44, 24, 12, 2 hosts → 192.168.1.0/26 (bc .63), 192.168.1.64/27 (bc .95),
  192.168.1.96/28 (bc .111), 192.168.1.112/30 (bc .115); free space starts at 192.168.1.116.
- 172.16.0.0/16, needs 340, 250, 31, 20, 8 hosts → 172.16.0.0/23 (bc 172.16.1.255),
  172.16.2.0/24 (bc 172.16.2.255), 172.16.3.0/26 (31 hosts needs 33 addresses → block 64; bc 172.16.3.63),
  172.16.3.64/27 (bc 172.16.3.95), 172.16.3.96/28 (bc 172.16.3.111).
- Exam Q1: 10.132.10.0/24, three LANs ≥ 45 hosts + one router-to-router link →
  LANs 10.132.10.0/26, 10.132.10.64/26, 10.132.10.128/26 (255.255.255.192, 62 hosts each);
  WAN 10.132.10.192/30 (R1 .193, R2 .194). Convention: router gets the first usable address,
  PCs get the next ones, PC default gateway = router interface on that LAN.

### 4.4 Route summarization
- 192.168.0.0/24 … 192.168.3.0/24 → **192.168.0.0/22** (255.255.252.0).
- 172.16.0.0/16 … 172.23.0.0/16 → **172.16.0.0/13** (255.248.0.0).

### 4.5 Simulator behaviour tests
- PC 192.168.1.10/24 pings 192.168.1.20/24 via switch: ARP request broadcast flooded to all switch
  ports except ingress; reply unicast; switch learns both MACs; ICMP echo request/reply forwarded
  to single ports.
- Same via hub: every frame appears on every other port.
- PC 192.168.1.10/24 (no gateway) pings 192.168.2.10 → fails at source with "no default gateway".
- PC A (192.168.1.10, gw .1) → Router (G0/0 192.168.1.1, G0/1 192.168.2.1) → PC B (192.168.2.10, gw 192.168.2.1):
  succeeds; at the router the frame's source MAC becomes G0/1's MAC and destination becomes PC B's MAC;
  IP addresses unchanged; TTL decremented by 1.
- Router interface without `no shutdown` → link amber, ping fails, explanation mentions `no shutdown`.
- Straight-through cable between two PCs in strict mode → link down, hint suggests crossover.

## 5. Exam / worksheet question styles for the practice generator

1. Given network + requirements (LAN host counts, router links, router model interfaces) → fill an
   addressing table (Device, Interface, IP, Mask, Default Gateway).
2. "Which subnet does host X mask M belong to?"
3. "How many subnets and hosts per subnet from network N mask M?" (classful default mask as reference).
4. "What is the broadcast address of network N mask M?"
5. Network ID / broadcast ID / mask for a.b.c.d/n.
6. Convert IP to binary (and back).
7. "Create k subnets from network N" → mask, number of subnets, first 3 ranges.
8. VLSM allocation from host requirements.
9. Route summarization of a list of networks.
10. True/false with correction (e.g. "Network ID: all host bits are ones?").
