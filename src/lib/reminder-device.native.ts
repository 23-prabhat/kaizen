import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import type { Reminder, ReminderKind, ReminderPermission, ReminderPlan } from './reminder-plan';

export const supportsReminders = Platform.OS === 'android' || Platform.OS === 'ios';
const prefix = 'daytabs-reminder:';
const channelId = 'daytabs-reminders';
const kinds: ReminderKind[] = ['todos', 'goals'];

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

function permissionStatus(settings: Notifications.NotificationPermissionsStatus): ReminderPermission {
  if (settings.granted || settings.ios?.status === Notifications.IosAuthorizationStatus.PROVISIONAL
    || settings.ios?.status === Notifications.IosAuthorizationStatus.EPHEMERAL) return 'granted';
  return settings.canAskAgain ? 'requestable' : 'blocked';
}

async function createChannel() {
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(channelId, {
      name: 'Kaizen reminders',
      importance: Notifications.AndroidImportance.DEFAULT,
    });
  }
}

export async function getReminderPermission() {
  return permissionStatus(await Notifications.getPermissionsAsync());
}

export async function requestReminderPermission() {
  await createChannel();
  return permissionStatus(await Notifications.requestPermissionsAsync({ ios: { allowAlert: true, allowSound: true } }));
}

let lastSync = Promise.resolve();

export function syncDeviceReminders(owner: string | undefined, plan: ReminderPlan) {
  const next = lastSync.then(async () => {
    const scheduled = await Notifications.getAllScheduledNotificationsAsync();
    const ownerPrefix = owner ? `${prefix}${owner}:` : null;
    const desired = new Map<string, Reminder>();
    if (ownerPrefix) {
      for (const kind of kinds) {
        for (const reminder of plan[kind] ?? []) desired.set(`${ownerPrefix}${kind}:${reminder.day}`, reminder);
      }
    }

    for (const item of scheduled) {
      if (!item.identifier.startsWith(prefix)) continue;
      if (!ownerPrefix || !item.identifier.startsWith(ownerPrefix)) {
        await Notifications.cancelScheduledNotificationAsync(item.identifier);
        continue;
      }
      const kind = item.identifier.slice(ownerPrefix.length).split(':')[0] as ReminderKind;
      if (plan[kind] === undefined) continue;
      const reminder = desired.get(item.identifier);
      if (reminder && item.content.data?.scheduledAt === reminder.at && item.content.body === reminder.body) {
        desired.delete(item.identifier);
      } else {
        await Notifications.cancelScheduledNotificationAsync(item.identifier);
      }
    }

    if (desired.size) await createChannel();
    for (const [identifier, reminder] of desired) {
      if (reminder.at <= Date.now()) continue;
      await Notifications.scheduleNotificationAsync({
        identifier,
        content: { title: reminder.title, body: reminder.body, sound: 'default', data: { scheduledAt: reminder.at } },
        trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: reminder.at, channelId },
      });
    }
  });
  lastSync = next.catch(() => undefined);
  return next;
}
