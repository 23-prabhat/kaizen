import { requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';
import { localDay } from './calendar';
import { dailyMotivation, motivationRotation } from './daily-motivation';

export async function syncMotivationWidget() {
  if (!requireOptionalNativeModule('ExpoWidgets')) return;

  if (Platform.OS === 'android') {
    const { default: widget } = await import('../widgets/motivation.android');
    widget.updateSnapshot(motivationRotation);
  } else if (Platform.OS === 'ios') {
    const { default: widget } = await import('../widgets/motivation.ios');
    const now = new Date();
    const dates = [now, ...Array.from({ length: 60 }, (_, i) =>
      new Date(now.getFullYear(), now.getMonth(), now.getDate() + i + 1))];
    widget.updateTimeline(dates.map(date => ({ date, props: dailyMotivation(localDay(date)) })));
  }
}
