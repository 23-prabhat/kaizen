import { calendarDays } from './calendar';

export type Motivation = { line: string; credit?: string };

const lines: Motivation[] = [
  // https://demonslayer-anime.com/news/?id=57317
  { line: 'Set your heart ablaze!', credit: 'Kyojuro Rengoku · Demon Slayer' },
  { line: 'Start with the task you can finish before the opening credits roll.' },
  { line: 'A rough first try is still a scene you can build on.' },
  { line: 'Make the first move. You can figure out the next one from there.' },
  { line: 'Take a breather. Even the training arc has rest days.' },
  { line: 'Save the biggest fight for after you’ve had lunch.' },
  { line: 'Write down what’s weighing on you. Pick one thing you can change.' },
  // https://www.crunchyroll.com/ko/news/features/2023/8/17/best-all-might-smashes-mha
  { line: 'Go beyond! Plus Ultra!', credit: 'All Might · My Hero Academia' },
  { line: 'If you’re stuck on the boss fight, try a different move.' },
  { line: 'Ten focused minutes can get a stalled scene moving again.' },
  { line: 'Give tomorrow’s episode a head start. Lay out what you’ll need.' },
  { line: 'Check off a small task before the big one takes over your whole day.' },
  { line: 'Ask your crew for help when the quest gets complicated.' },
  { line: 'Put the phone down for one song and see how much you get done.' },
  // https://www.crunchyroll.com/pl/news/features/2023/3/6/feature-bet-on-luffy-how-one-boy-inspires-the-world-of-one-piece-to-hope
  { line: 'I’m going to be the King of the Pirates.', credit: 'Monkey D. Luffy · One Piece' },
  { line: 'Name the part you’re avoiding. It might be smaller than it feels.' },
  { line: 'Let someone see the rough draft while there’s still time to help.' },
  { line: 'Give yourself credit for coming back after a bad episode.' },
  { line: 'The quiet practice scenes are where the new moves come from.' },
  { line: 'If today’s plan got derailed, pick one thing worth getting back to.' },
  { line: 'A side quest is fine. Write down where you left the main one.' },
  // https://www.crunchyroll.com/news/features/2024/8/1/usopp-one-piece-25-anniversary
  { line: 'Big or small, I’m going to fight for it and I’m going to fight for me.', credit: 'Usopp · One Piece' },
  { line: 'Give that half-finished task one more scene before you call it a day.' },
  { line: 'A good teammate tells you when you’ve taken on too much.' },
  { line: 'Pick a skill you want to get better at and give it one honest try.' },
  { line: 'Leave yourself a note about what worked. It’ll help on the next run.' },
  { line: 'When your energy is low, shrink the task until it fits.' },
  { line: 'Celebrate the finish before you write the next list.' },
  { line: 'Try the awkward first step while you still have the nerve.' },
  { line: 'Put the tools away when you’re done. Tomorrow’s you will thank you.' },
];

export const motivationRotation = { firstDay: '2026-11-12', lines };

export function dailyMotivation(day: string): Motivation {
  const offset = calendarDays(motivationRotation.firstDay, day) - 1;
  const index = ((offset % lines.length) + lines.length) % lines.length;
  return lines[index];
}
