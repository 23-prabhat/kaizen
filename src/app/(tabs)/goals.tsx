import { Checklist } from '@/components/checklist';
import { Heading, Page } from '@/components/ui';
import { goalsScope } from '@/lib/planner';

export default function Goals() {
  return <Page>
    <Heading>Goals</Heading>
    <Checklist scope={goalsScope} />
  </Page>;
}
