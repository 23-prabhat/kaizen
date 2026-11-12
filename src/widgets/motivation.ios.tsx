import { Text, VStack } from '@expo/ui/swift-ui';
import { containerBackground, font, foregroundStyle, frame, padding } from '@expo/ui/swift-ui/modifiers';
import { createWidget, type WidgetEnvironment } from 'expo-widgets';
import { dailyMotivation, type Motivation } from '../lib/daily-motivation';
import { localDay } from '../lib/calendar';

function MotivationWidget({ line, credit }: Motivation, environment: WidgetEnvironment) {
  'widget';
  return <VStack alignment="leading" spacing={5} modifiers={[
    padding({ all: 12 }),
    frame({ maxWidth: Infinity, maxHeight: Infinity, alignment: 'leading' }),
    containerBackground('#FFD947', 'widget'),
  ]}>
    <Text modifiers={[font({ size: 11, weight: 'bold' }), foregroundStyle('#3E2F2C')]}>
      Kaizen · Daily motivation
    </Text>
    <Text modifiers={[font({ size: environment.widgetFamily === 'systemSmall' ? 15 : 20, weight: 'bold' }), foregroundStyle('#231A2A')]}>
      {credit ? `“${line}”` : line}
    </Text>
    {credit && <Text modifiers={[font({ size: 10 }), foregroundStyle('#3E2F2C')]}>{credit}</Text>}
  </VStack>;
}

export default createWidget('DaytabsMotivation', MotivationWidget, dailyMotivation(localDay()));
