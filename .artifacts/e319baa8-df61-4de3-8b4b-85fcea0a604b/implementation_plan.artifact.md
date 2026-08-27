# Update Goat Profile Summary: Remove Redundant Pedigree, Add Health and Insurance Status

This plan outlines the changes to the Goat Profile screen to remove redundant Dam and Sire fields from the summary card and add calculated Insurance and Health Status based on farm records.

## User Review Required

> [!IMPORTANT]
> - **Health Status Logic**: Adult goats (>= 6 months) start at 60% health. Each active "Deworming" and "Vaccination" record (where the next due date has not passed) adds 20%, up to 100%. Kids (< 6 months) are always at 100%.
> - **Insurance Status Logic**: Displays "Active" if there's an insurance record with an expiry date in the future, otherwise "Not Insured" or "Expired".

## Proposed Changes

### app/src/main/java/com/goatkeeper/app/util/FarmUtils.kt

#### [MODIFY] [FarmUtils.kt](file:///C:/My Android Projects/GoatKeeper/app/src/main/java/com/goatkeeper/app/util/FarmUtils.kt)
- Add `calculateHealthStatus(goat, records, today)` function.
- Add `calculateInsuranceStatus(records, today)` function.

---

### app/src/main/java/com/goatkeeper/app/GoatKeeperApp.kt

#### [MODIFY] [GoatKeeperApp.kt](file:///C:/My Android Projects/GoatKeeper/app/src/main/java/com/goatkeeper/app/GoatKeeperApp.kt)
- Update `GoatProfile` to pass `records` to `GoatInfoTab`.
- Update `GoatInfoTab` to:
    - Accept `records: List<FarmRecord>`.
    - Remove redundant "Dam" and "Sire" `InfoRow`s (since they are in the Family Tree).
    - Add "Insurance" and "Health" `InfoRow`s using the new utility functions.

## Verification Plan

### Automated Tests
- I will verify the logic by running the app and checking the profile for goats with different record states.

### Manual Verification
- Create a goat under 6 months -> verify Health is 100%.
- Create a goat over 6 months with no records -> verify Health is 60% and Insurance is "Not Insured".
- Add an active Deworming record -> verify Health becomes 80%.
- Add an active Vaccination record -> verify Health becomes 100%.
- Add an expired Insurance record -> verify Insurance status.
- Verify Dam and Sire fields are gone from the summary card but still visible in the Family Tree.
