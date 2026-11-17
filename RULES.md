# DRIP Rules v0.1 (draft)

Product spec for competition mechanics, community standards and content policy.
Launch market: **Porto, Portugal** only. Mozambique is a later phase.
Not legal advice: have the policy sections reviewed by a lawyer before public launch.

## 1. Principles

1. **Judge the outfit, never the person.** No ranking of bodies, faces or personal worth.
2. **No popularity advantage.** Follower count, paid status and clothing price must not influence results.
3. **Fairness is a product feature.** Rules are public and applied identically to everyone.

## 2. Terms

| Term | Definition |
|---|---|
| **Vote** | A voter's choice of the outfit they prefer in a showdown or challenge. Not a currency; cannot be bought, sold or transferred. |
| **Fire 🔥** | The feed reaction (formerly "Props"). A social signal only: it never affects battle results, challenge placement, Drip Points or Drip Score. |
| **Drip Points** | Earned only from battle and challenge outcomes. Drive leaderboards and badges. |
| **Drip Score** | A skill rating (Elo/Glicko-style) updated after each battle. Reflects consistency against opponents of varying strength. |

### Drip Points
| Outcome | Points |
|---|---|
| Win | +10 |
| Draw | +5 |
| Loss | +2 (participation) |
| Void battle | 0 |

**Vote to play:** a user must have cast 10 votes (showdown or challenge) per showdown they start (first showdown needs 10, the next 20 cumulative, and so on). Votes themselves earn no points.

Daily cap: 100 points per user. Showdown cap: at most 3 showdowns per user per 24 hours (stops points farming).

### Drip Score
- Starts at 1000. Hidden until the user completes 5 battles.
- Beating a higher-rated opponent increases the score more than beating a lower-rated one.
- Seasons (quarterly) soft-reset scores toward 1000 so newcomers can climb.

## 3. 1v1 Battle (Showdown) lifecycle

1. **Create.** A user submits one outfit to start a showdown. No page lists open showdowns.
2. **Match.** The system assigns the opponent, with a similar Drip Score. The opponent cannot be a follower, a followed account, or a recent interactor (comments or messages in the last 30 days). Users cannot pick their opponent.
3. **Lock.** Once matched, the battle is locked. Outfits cannot be edited, and participants cannot promote or interact with the battle. Sharing opens once the result is final (closed battles only).
4. **Distribute.** The battle is shown to voters at random. Excluded voters:
   - followers and followed accounts of either participant
   - accounts younger than 24 hours
   - the participants themselves
5. **Vote anonymously.** Usernames are hidden during voting and revealed after the result. Live vote counts are hidden until close.
6. **Close.**
   - Voting window: 24 hours.
   - Quorum: **20 valid votes**.
   - If quorum is not met, the window extends once, up to 72 hours total.
   - Still no quorum: the battle is **void** (no points, no score change, no penalty).
7. **Result.** The outfit with the majority of valid votes wins. A margin of 2 votes or fewer is a draw (a percentage margin is meaningless at small vote counts).
8. **Display.** Participants see the result on their profile; the battle appears as a read-only placeholder.

### Voting rules
- One vote per account per battle. Votes are final.
- No voting on your own battle.
- Votes from flagged accounts are discarded.

## 4. Anti-manipulation

- Discard votes from accounts flagged as fake, automated or from clustered devices.
- Monitor vote spikes and coordinated patterns; hold the result for review when triggered.
- Repeated matching of the same two users is limited (max 1 battle per pair per 14 days).
- Penalties: result voided, points removed, then suspension for repeat offences.

## 5. Challenges (MVP: one format)

### Lifecycle
1. **Submission window** (fixed, e.g. 7 days). One entry per user per challenge; entry must match the theme.
2. **Voting window** (e.g. 3 days) opens after submissions close. No entries after that.
3. **Settlement** when the voting window ends. Placement, points and Drip Score are applied once.

### Voting
- Voters are shown **pairs** of entries at random (Look A / Look B) and pick one. Pairwise picks are aggregated into a ranking (win rate), which keeps votes simple and comparable across entries.
- Same anonymity as showdowns: no names, handles, follower counts or running tallies until settlement.
- Same eligibility as showdowns: account at least 24 hours old, not flagged, not following the entrant, not blocked either way.
- Entrants cannot vote on their own entry; they may vote on others.
- Each entry must receive at least 20 valid votes to be ranked. Entries below the quorum are marked "not enough votes" and receive the entry points only.
- The system balances exposure so every entry gets a similar number of impressions.
- The feed Fire reaction, follower count and profile do not influence the ranking.

### Placement and points
- Ranked by win rate over valid votes. Ties are broken by more valid votes, then earlier submission.
- Drip Points: 1st +30, 2nd +20, 3rd +15, other ranked entries +5 (subject to the daily cap).
- Challenge results do not change Drip Score in the MVP (only showdowns do).

### Integrity
- Vote-spike and coordinated-pattern checks from section 4 apply per entry; flagged results are held for review.
- Entries removed for guideline breaches are excluded and their votes discarded.

### Face and body bias
Faces and bodies attract votes unrelated to the outfit. To reduce this:
- Showdown and challenge photos should be framed on the outfit; a cropped, covered or turned-away face is encouraged and the in-app camera guide shows a neck-down frame.
- Faces are allowed but optional. Voters are told: "Vote on the outfit, not the person."
- No beauty filters or face-edit effects on battle photos.
- Reports for "not an outfit photo" (e.g. selfie-only) can disqualify an entry.
- Beta metric: compare win rates of face-visible vs face-hidden entries; if the gap is large, make face-hidden framing the default.

## 6. Community guidelines

**Not allowed:**
- Nudity or sexual content
- Hate speech, harassment or bullying
- Body shaming or comments about someone's body
- Impersonation or fake accounts
- Photos of other people without consent
- Spam, vote trading or buying engagement
- Illegal content, counterfeit selling

**Minimum age: 16.** Users under 16 may not register. (Portugal's digital-consent age is 13; DRIP applies a stricter age because strangers vote on photos. Confirm 16 vs 18 with a lawyer.)

**Enforcement ladder:** warning, 7-day suspension, permanent ban. Severe violations may skip steps. Every action can be appealed through in-app support; target response 7 days.

**Tools (MVP):** report any outfit, user or comment; block users; moderation queue with a 48-hour review target.

## 7. Privacy defaults

- Profiles default to public outfits, with controls for visibility and who can message.
- Collect the minimum data needed. GDPR applies (Portugal).
- Users can export or delete their data from settings.

## 8. Content licensing and removal

- **Ownership:** users keep ownership of what they upload.
- **Licence to DRIP:** a limited, non-exclusive, worldwide licence to host, display and distribute the content within the app, for the purpose of operating the service. It ends when the content is deleted.
- **Brand use:** DRIP will not use user content in advertising or brand campaigns without separate, explicit opt-in.
- **User warranty:** users confirm they own the content or have permission and that it shows no one without their consent.
- **Deletion:** removed from public view immediately, and deleted from systems within 30 days (backups excepted, purged on rotation).
- **Account deletion:** deletes profile and outfits. Battle results remain only as anonymised counts.
- **Takedown requests:** copyright or privacy complaints can be sent via an in-app form and a contact email. Target first response: 48 hours.

## 9. Constraints, local league and sharing

- **Constraints:** a challenge can carry a constraint (Open, Budget, Thrift, Closet). Entrants tick a pledge to enter. Price never decides a result; the constraint only defines who competes.
- **Porto league:** a local leaderboard sits beside the global one (launch city: Porto).
- **Sharing:** only after a battle or challenge is closed, and only participants can share. Channels: WhatsApp chat and status, Facebook, Instagram story and feed, system share sheet. Shares never reveal who voted.

## 10. Moderation SLA

- Nudity, minors and safety reports: handled within hours via a priority queue; the founder is first responder at launch.
- Spam and other reports: within 48 hours.
- DSA: notice-and-action and an appeal route for removals. Lawyer to confirm obligations.

## 11. Open items

- [x] Anonymous voting confirmed.
- [ ] Decide whether face-hidden framing becomes mandatory after beta data.
- [x] Challenge voting (pairs, quorum, exposure balancing, win-rate ranking) is implemented; Fire no longer affects placement.
- [ ] Vote-spike checks for challenge votes; Instagram Story needs a real Facebook App ID; WhatsApp status goes through WhatsApp's picker.
- [ ] Tune quorum (20) and voting window against real beta participation.
- [ ] Lawyer review: privacy policy, terms of service, minors, content licence.
- [ ] Decide skill-matching thresholds (Drip Score range) once there are enough users.
- [ ] Mozambique phase: legal and data-protection review before expansion.

## 12. Outfit photos

- Photos are picked from the gallery (no camera access, no photo-library permission needed).
- Neck-down only: the app shows a face-zone guide and crops outfit images bottom-aligned, trimming the top of the frame. No beauty filters.
- Photos stay on the device until cloud storage (Firebase) is added. The share card uses the photo when present.
- Open: server-side face detection and moderation before a photo goes public.

## 13. Backend (Firebase project drip-ae9a5)

Source in `backend/`. Deploy: `cd backend && npm --prefix functions install && npx firebase-tools login && npx firebase-tools deploy` (needs the Blaze plan for Functions). Logic is a pure TypeScript port in `functions/src/rules.ts` (`npm --prefix functions test`).

- **Server-only writes:** `userStats` (rating, counters, flags), `pointEvents`, `battles`, votes, challenge entry stats. Clients can't change ratings, points or results.
- **Callables (europe-west1):** `createProfile` (16+ confirmation, unique handle), `startBattle` (vote-to-play, 3/day), `castVote`, `castChallengeVote`; moderator-only `resolveBattleReview`, `closeChallenge` (custom claim `moderator`).
- **Scheduled `settleBattles`** every 5 min: expiry, matching, closing. `onReportCreated` auto-hides at 3 reports.
- **Anonymity:** votes live in `battles/{id}/votes/{voterUid}` and are readable only by that voter. Tallies (`votesA`/`votesB`) are published only when a battle closes.
- **Not yet enforced server-side:** challenge-vote spike checks (battle spikes are enforced), and photo uploads are not content-moderated.
