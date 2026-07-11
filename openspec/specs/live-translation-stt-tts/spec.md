# Live Translation STT/TTS Specification

## Status
Proposed — awaiting implementation of the `add-live-translation-stt-tts` change.

## Overview
Shannon today supports text messaging (LXMF) and voice calls (LXST) over the
decentralized Reticulum network. It is serverless and E2EE by design: there is no
SFU, no media server, and no central place where audio could be tapped or
transcribed.

This capability adds **on-device** Speech-to-Text (live captions / transcription)
and Text-to-Speech (synthesized spoken translation) so users who speak different
languages can communicate more easily. Critically, it preserves Shannon's
architecture:

- **No server is introduced.** All ML inference (STT, VAD, TTS) runs locally on
  the speaker's and listener's devices.
- **Raw audio never leaves the device.** Each device transcribes its own
  microphone input locally and sends only the resulting **text** to the peer.
- **Only cheap transcript/translation text traverses the network**, over the
  existing E2EE Reticulum links (LXST during a call, LXMF for async messages) —
  well suited to Shannon's low-bandwidth radio/BLE/TCP transports.

This document becomes the source of truth once the `add-live-translation-stt-tts`
change is merged.

## Related
- Change: `changes/add-live-translation-stt-tts/` (proposal, design, tasks, delta)
