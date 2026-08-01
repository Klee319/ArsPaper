package com.arspaper.mana;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaBarVisibilityPolicyTest {

    @Test
    void spellBookCustomItemIdsAreRelevant() {
        assertTrue(ManaBarVisibilityPolicy.isRelevantItem(
                "spell_book_novice", 1, "book-uuid", null, null));
        assertTrue(ManaBarVisibilityPolicy.isRelevantItem(
                "spell_book_apprentice", 2, "book-uuid", null, null));
        assertTrue(ManaBarVisibilityPolicy.isRelevantItem(
                "spell_book_archmage", 3, "book-uuid", null, null));
    }

    @Test
    void configuredSpellBookTierMayUseAnyNonBlankCustomId() {
        assertTrue(ManaBarVisibilityPolicy.isRelevantItem(
                "grandmasters_arcane_tome", 4, "book-uuid", null, null));
    }

    @Test
    void completeBoundSpellReferenceIsRelevant() {
        assertTrue(ManaBarVisibilityPolicy.isRelevantItem(
                null, null, null,
                "4a0cb95a-e391-47c7-8755-ff07a9420937", 0));
    }

    @Test
    void incompleteOrInvalidBoundSpellReferenceIsNotRelevant() {
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                null, null, null, null, 0));
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                null, null, null,
                "4a0cb95a-e391-47c7-8755-ff07a9420937", null));
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                null, null, null, "   ", 0));
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                null, null, null,
                "4a0cb95a-e391-47c7-8755-ff07a9420937", -1));
    }

    @Test
    void incompleteSpellBookMarkersAndUnrelatedItemsAreNotRelevant() {
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                "ember_wand", null, null, null, null));
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                "spell_book_novice", null, "book-uuid", null, null));
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                "spell_book_novice", 1, null, null, null));
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                null, 1, "book-uuid", null, null));
        assertFalse(ManaBarVisibilityPolicy.isRelevantItem(
                null, null, null, null, null));
    }

    @Test
    void barShowsWhenEitherHandIsRelevantAndHidesWhenNeitherIs() {
        assertTrue(ManaBarVisibilityPolicy.shouldShow(true, false));
        assertTrue(ManaBarVisibilityPolicy.shouldShow(false, true));
        assertTrue(ManaBarVisibilityPolicy.shouldShow(true, true));
        assertFalse(ManaBarVisibilityPolicy.shouldShow(false, false));
    }
}
