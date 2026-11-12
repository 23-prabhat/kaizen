import { Column, Text } from '@expo/ui/jetpack-compose';
import { background, fillMaxSize, paddingAll } from '@expo/ui/jetpack-compose/modifiers';
import { createWidget } from 'expo-widgets';
import { motivationRotation } from '../lib/daily-motivation';

function MotivationWidget({ firstDay, lines }: typeof motivationRotation) {
  'widget';
  const now = new Date();
  const date = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  const offset = (date - Date.parse(firstDay)) / 86_400_000;
  const { line, credit } = lines[((offset % lines.length) + lines.length) % lines.length];

  return <Column modifiers={[fillMaxSize(), background('#FFD947'), paddingAll(12)]}>
    <Text color="#3E2F2C" style={{ fontSize: 11, fontWeight: 'bold' }}>Kaizen · Daily motivation</Text>
    <Text color="#231A2A" style={{ fontSize: 18, fontWeight: 'bold' }} maxLines={4}>
      {credit ? `“${line}”` : line}
    </Text>
    {credit && <Text color="#3E2F2C" style={{ fontSize: 11 }} maxLines={2}>{credit}</Text>}
  </Column>;
}

export default createWidget('DaytabsMotivation', MotivationWidget, motivationRotation);
