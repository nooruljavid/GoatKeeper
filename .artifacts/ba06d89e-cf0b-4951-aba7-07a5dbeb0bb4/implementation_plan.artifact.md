# Implementation Plan - Goat Family Tree

This plan adds a visual "Family Tree" flowchart to the Goat Profile screen. It will display the Dam (Mother) and Sire (Father) of the current goat in a clear, professional diagram.

## Proposed Changes

### UI & Visualization

#### [MODIFY] [GoatKeeperApp.kt](file:///C:/Users/snoor/OneDrive/Desktop/French Document/GoatKeeper/app/src/main/java/com/goatkeeper/app/GoatKeeperApp.kt)
1.  **New Composable: `FamilyTreeNode`**:
    *   A small card component to represent a single goat in the tree.
    *   Displays: `Goat ID - Name` (Top) and `Breed` (Bottom).
2.  **New Composable: `FamilyTree`**:
    *   Takes the current goat and the list of all goats.
    *   Uses a `Column` + `Row` layout to position the **Dam** and **Sire** at the top.
    *   Includes a **Visual Connector** (lines/arrows) that flows down to the **Current Goat**.
    *   Handles cases where the Dam or Sire are not in the database (shows "Unknown" or just the ID).
3.  **Update `GoatInfoTab`**:
    *   Insert the `FamilyTree` component after the main details card.
    *   Add a "Pedigree / Family Tree" section header.

## Verification Plan

### Manual Verification
1.  Open a goat profile that has both a Dam and Sire assigned.
2.  Scroll down to the **Info** tab.
3.  **Verify**: You see a new "Family Tree" section.
4.  **Verify**: The Dam (left) and Sire (right) are displayed with their IDs, Names, and Breeds.
5.  **Verify**: Lines correctly connect the parents to the current goat.
6.  Open a goat with "Unknown" parents and verify the tree still looks clean (handles missing data gracefully).
