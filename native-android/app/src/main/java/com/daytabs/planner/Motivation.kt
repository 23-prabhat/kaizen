package com.daytabs.planner

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class Motivation(val line: String, val credit: String? = null) {
    val displayLine: String get() = if (credit == null) line else "“$line”"
}

private val firstMotivationDay = LocalDate.of(2026, 11, 12)
private val quotes = listOf(
    Motivation("Set your heart ablaze!", "Kyojuro Rengoku · Demon Slayer"),
    Motivation("Start with the task you can finish before the opening credits roll."),
    Motivation("A rough first try is still a scene you can build on."),
    Motivation("Make the first move. You can figure out the next one from there."),
    Motivation("Take a breather. Even the training arc has rest days."),
    Motivation("Save the biggest fight for after you’ve had lunch."),
    Motivation("Write down what’s weighing on you. Pick one thing you can change."),
    Motivation("Go beyond! Plus Ultra!", "All Might · My Hero Academia"),
    Motivation("If you’re stuck on the boss fight, try a different move."),
    Motivation("Ten focused minutes can get a stalled scene moving again."),
    Motivation("Give tomorrow’s episode a head start. Lay out what you’ll need."),
    Motivation("Check off a small task before the big one takes over your whole day."),
    Motivation("Ask your crew for help when the quest gets complicated."),
    Motivation("Put the phone down for one song and see how much you get done."),
    Motivation("I’m going to be the King of the Pirates.", "Monkey D. Luffy · One Piece"),
    Motivation("Name the part you’re avoiding. It might be smaller than it feels."),
    Motivation("Let someone see the rough draft while there’s still time to help."),
    Motivation("Give yourself credit for coming back after a bad episode."),
    Motivation("The quiet practice scenes are where the new moves come from."),
    Motivation("If today’s plan got derailed, pick one thing worth getting back to."),
    Motivation("A side quest is fine. Write down where you left the main one."),
    Motivation("Big or small, I’m going to fight for it and I’m going to fight for me.", "Usopp · One Piece"),
    Motivation("Give that half-finished task one more scene before you call it a day."),
    Motivation("A good teammate tells you when you’ve taken on too much."),
    Motivation("Pick a skill you want to get better at and give it one honest try."),
    Motivation("Leave yourself a note about what worked. It’ll help on the next run."),
    Motivation("When your energy is low, shrink the task until it fits."),
    Motivation("Celebrate the finish before you write the next list."),
    Motivation("Try the awkward first step while you still have the nerve."),
    Motivation("Put the tools away when you’re done. Tomorrow’s you will thank you."),
)

internal fun dailyMotivation(day: LocalDate): Motivation {
    val offset = ChronoUnit.DAYS.between(firstMotivationDay, day)
    return quotes[Math.floorMod(offset, quotes.size.toLong()).toInt()]
}
