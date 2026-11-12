import { Checklist, DateStrip } from '@/components/checklist';
import { Heading, Page } from '@/components/ui';
import { dayLabel } from '@/lib/calendar';
import { useToday } from '@/lib/clock';

export default function Todos() {
  const day = useToday();
  return <Page>
    <Heading>To-dos</Heading>
    <Checklist scope={{ kind: 'todo', day }} header={(done, total) =>
      <DateStrip label={dayLabel(day)} detail={`${done} of ${total} done`} />} />
  </Page>;
}
