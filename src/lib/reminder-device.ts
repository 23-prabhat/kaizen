import type { ReminderPermission, ReminderPlan } from './reminder-plan';

export const supportsReminders: boolean = false;
export async function getReminderPermission(): Promise<ReminderPermission> { return 'blocked'; }
export async function requestReminderPermission(): Promise<ReminderPermission> { return 'blocked'; }
export async function syncDeviceReminders(_owner: string | undefined, _plan: ReminderPlan): Promise<void> {}
