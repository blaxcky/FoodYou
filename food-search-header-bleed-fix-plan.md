# Fix Food Search Header Bleed-Through

## Summary

Fix the food search screen so result list content cannot show through the transparent gaps between the search/filter chips. The change is limited to the food search header layering and screenshot coverage.

## Implementation Changes

- In `FoodSearchApp.kt`, split the header modifier into:
  - an outer full-width/safe-area modifier used for `zIndex`, `onSizeChanged`, and background masking
  - an inner `padding(vertical = 8.dp)` modifier for the actual header content
- Render `FoodSearchHeader` inside an opaque `Surface` or `Box` using `MaterialTheme.colorScheme.background`, matching the Scaffold background.
- Keep the existing measured-header-height offset for `FoodSearchResults`; attach measurement to the opaque outer header so the list still starts below the complete header.
- Do not change chip styling, source order, search bar styling, or food result list behavior.

## Public Interfaces

- No public API, ViewModel, domain, or state contract changes.
- Only internal Compose layout behavior changes.

## Tests

- Update `FoodSearchAppScreenshotTest` so the food search UI state includes the chip set visible in the screenshot: `All`, `YourFood`, `Recent`, and `FDDB`, with stable counts.
- Keep the overlay test scrolled (`scrollToItem(4, 24)`) so list content would be visible if the header mask regresses.
- Record/commit the missing overlay Roborazzi reference image: `FoodSearchAppScreenshotTest.overlay-scrolled-search.png`.
- Run targeted verification:
  - `:app:testDebugUnitTest --tests com.maksimowiczm.foodyou.app.ui.food.search.FoodSearchAppScreenshotTest`
  - `:app:compileDebugKotlinAndroid`
- Run broader Roborazzi verification after recording references. Current pre-change run showed unrelated failures in `CalorieWidgetModelTest` at lines 52 and 69; if still present, document them separately and do not mix them into this UI fix.

## Commit And Sync

- After implementation and relevant verification, run `git status --short`, commit the cohesive UI fix plus screenshot reference, then sync with GitHub via `gh`/`git push` per repo instructions.

## Assumptions

- The bug is the overlay header allowing transparent areas between chips to reveal scrolled result content.
- The intended visual result is the same layout as now, just with the header acting as an opaque mask over the results.
