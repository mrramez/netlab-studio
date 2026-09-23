# NetLab Studio — Product Spec

## 1. Audience and goal

University students (2nd year, "Networks I" level) who find networking abstract. The app must make
invisible things visible: what a frame looks like, why a switch floods, why a ping to another subnet
needs a default gateway, what changes at each hop. Tone: clear, friendly, precise. No jargon without
an explanation on first use (tooltip or glossary link).

## 2. Tech stack

| Area | Choice |
|---|---|
| Language / runtime | Java 21 (LTS) |
| UI | JavaFX 21 (FXML optional; CSS themes light/dark) |
| Build | Maven + Maven Wrapper |
| Tests | JUnit 5, AssertJ; TestFX only for a few UI smoke tests (optional) |
| JSON | Jackson (databind) with explicit DTOs |
| Formatting | Spotless + google-java-format |
| Coverage | JaCoCo (gate: ≥ 80% line coverage on `core.net`, `core.sim`, `core.cli`) |
| Packaging | jlink + jpackage → Windows installer (.msi or .exe) built in GitHub Actions |
| Fonts | Bundle Noto Sans + Noto Sans Arabic (SIL OFL) for consistent rendering |

## 3. Architecture

```
io.github.mrramez.netlabstudio
├── app            Main, bootstrap, DI wiring (plain constructors, no framework)
├── core
│   ├── net        Ipv4Address, SubnetMask, Cidr, MacAddress, IpClass, SubnetCalculator,
│   │              VlsmPlanner, RouteSummarizer, BinaryConverter  (pure, no deps)
│   ├── model      Device (abstract), Pc, Server, Hub, Repeater, Bridge, Switch, Router,
│   │              Port/Interface, Link, CableType, Topology
│   ├── sim        SimulationEngine (discrete events), Frame, ArpMessage, Ipv4Packet, IcmpMessage,
│   │              SimEvent (who, what, layer, explanationKey, args), device behaviours
│   ├── cli        IosSession, CliMode state machine, CommandParser, command handlers,
│   │              abbreviation matching ("conf t", "int g0/0", "sh ip int br")
│   ├── lesson     Lesson, LessonStep, LessonRepository (loads from resources)
│   ├── quiz       Question (MCQ, true/false, fill-in, numeric/IP answer), Quiz, Grader
│   ├── scenario   Scenario, GoalChecker (e.g. "PC0 can ping PC3"), Hint
│   └── persistence TopologyDto + mapper, ProgressStore (~/.netlabstudio/progress.json)
└── ui             JavaFX views, controllers, custom controls, animations, i18n, themes
```

Rules: `core.*` never imports JavaFX. The simulation engine is deterministic (seeded MAC generation,
no wall-clock timing) so tests can assert exact event sequences. The UI plays events back with animation.

## 4. Main screens

1. **Home** — cards: Lessons, Simulator, Subnetting Lab, CLI Practice, Scenarios, Progress.
   Language toggle (EN/AR) and theme toggle (light/dark) always visible in the top bar.
2. **Lessons** — left: lesson list with progress ticks; centre: lesson steps (text + interactive
   widget); bottom: Previous / Next; end of lesson: short quiz.
3. **Simulator** — left palette (PC, Laptop, Server, Hub, Switch, Router, + Repeater/Bridge in lesson
   mode); centre canvas; right inspector (selected device config / PDU details);
   bottom event log with plain-language explanations. Toolbar: select, cable tool (choose cable type),
   delete, note, **Realtime / Step mode**, play/pause/step/reset, speed slider, save/open.
4. **Subnetting Lab** — calculator + step-by-step explanation + practice generator (exam style).
5. **CLI Practice** — terminal pane attached to a router or switch in the current topology.
6. **Scenarios** — guided tasks with goal checklist, hints, and "check my work".

## 5. Simulator behaviour (v1 — "realistic but simplified")

Devices and what they must do:

- **PC / Laptop / Server**: one NIC (MAC, IPv4, mask, default gateway). Commands in a small
  command-prompt window: `ipconfig`, `ipconfig /all`, `ping <ip>`, `arp -a`, `arp -d`.
  Forwarding decision: AND(own IP, mask) vs AND(dest IP, mask) → local → ARP for dest;
  remote → ARP for default gateway. No/invalid gateway → "Destination host unreachable" with explanation.
- **Hub** (and Repeater): layer 1, repeats bits out of every other port; one collision domain.
- **Bridge / Switch**: learns source MAC → port; forwards known unicast to one port, floods unknown
  unicast and broadcast; MAC table visible in the inspector and via `show mac address-table`.
  Each port is its own collision domain; the whole switch is one broadcast domain (VLANs in v2).
- **Router**: interfaces are administratively down until `no shutdown`; needs IP + mask per interface;
  connected routes appear automatically when the interface is up/up; decrements TTL, drops at 0;
  replaces source/destination MAC at every hop while source/destination IP stay the same;
  has its own ARP table; does not forward broadcasts.
- **ARP**: request is broadcast (ff:ff:ff:ff:ff:ff), reply is unicast; caches populate on both sides.
- **ICMP**: echo request / echo reply; first ping may lose the first packet while ARP resolves
  (explain why — this matches real Cisco behaviour).
- **Cables**: straight-through, crossover, serial (router–router, phase 2), console (CLI only).
  "Strict cabling" setting (default ON, as in the lab): wrong cable type → link stays down (red),
  with a tooltip explaining which cable is correct and why. When OFF → Auto-MDIX (either works).
- **Link state**: green = up, red = down, amber = interface administratively down.
- **Step mode**: each event shows: the device, the OSI layer (colour), the PDU (with expandable
  headers: Ethernet → IPv4 → ICMP/ARP), and a one- or two-sentence explanation of *why*.
- **Collision/broadcast domain overlay**: toggle that colours domains on the canvas.

Phase 2 adds: static and default routes, DHCP (router pool + DORA animation), VLANs + 802.1Q trunks,
`tracert`/`traceroute` (TTL expiry, ICMP time exceeded), serial links, route summarization in the table.

## 6. Cisco IOS CLI (v1 subset)

Modes: User EXEC `>` → `enable` → Privileged `#` → `configure terminal` → Global `(config)#` →
`interface X` → `(config-if)#` / `line console 0`, `line vty 0 4` → `(config-line)#`. `exit`, `end`, `Ctrl+Z`.
Commands: `hostname`, `enable password`, `enable secret`, `password`, `login`, `banner motd #...#`,
`service password-encryption`, `ip address`, `no shutdown`/`shutdown`, `description`,
`duplex`, `speed`, `interface vlan 1` + `ip default-gateway` (switch SVI),
`ip domain-name`, `crypto key generate rsa`, `username ... secret ...`, `transport input ssh`,
`show running-config`, `show startup-config`, `show ip interface brief`, `show interfaces`,
`show ip route`, `show ip arp`, `show mac address-table`, `show version` (fake but plausible),
`copy running-config startup-config`, `write memory`, `erase startup-config`, `reload`, `?` help,
unique-prefix abbreviations, `% Invalid input detected at '^' marker.` with caret position,
`% Incomplete command.`, `% Ambiguous command:`. Tab completion and up-arrow history.
Every accepted command changes the same model the simulator uses.

## 7. Subnetting Lab

- Calculator: input `a.b.c.d` + mask or `/n` → class, default mask, network, broadcast, first/last host,
  usable hosts, wildcard, binary view with network/subnet/host bits coloured, "block size" explanation.
- Subnet by number of subnets or hosts per subnet, list first N subnets.
- VLSM planner: base network + list of host requirements → sorted allocation table (largest first).
- Route summarization: list of networks → summary with binary common-prefix view.
- Practice generator: random questions in the same styles as the course exams (see CURRICULUM.md §5),
  graded with step-by-step worked solutions.

## 8. Lessons, quizzes, scenarios

See `docs/CURRICULUM.md` for the lesson list. Each lesson = 4–10 steps, each step = short text
(≤ 120 words) + one interactive widget or diagram + optional "Did you know / Common mistake" box.
Quiz = 5–10 questions; explanations shown for every answer. Content lives in
`src/main/resources/lessons/<id>/{en,ar}.json` so it can be edited without code changes.
Progress (completed steps, quiz best scores) saved locally as JSON.

## 9. Non-functional

- Starts in < 3 s on a mid-range laptop; canvas smooth with 30 devices.
- Keyboard accessible; minimum font 14 px; colour is never the only signal (add icons/labels).
- Works offline. Window min size 1100×700.

## 10. DevSecOps / repository

- GitHub Actions: `ci.yml` (build + test + coverage on ubuntu and windows, on push/PR),
  `codeql.yml` (Java SAST), `gitleaks` secret scan, `dependency-review` on PRs,
  `release.yml` (on tag `v*`: jpackage Windows installer + CycloneDX SBOM + SHA-256 checksums
  attached to the GitHub Release). Dependabot for Maven and GitHub Actions.
- `SECURITY.md`, `LICENSE` (MIT), `CONTRIBUTING.md`, issue/PR templates, README badges.
- README: screenshots/GIF, features, download link, build instructions, architecture diagram,
  security practices section.
