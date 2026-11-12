import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';

export const avatarUriPrefix = 'data:image/png;base64,';
export const maxAvatarPhotos = 4;

export type AvatarPhoto = { id: string; uri: string };
export type AvatarSelection = { photos: AvatarPhoto[]; fixedId: string | null };

type Avatar = AvatarSelection & {
  ready: boolean;
  save: (selection: AvatarSelection) => Promise<void>;
};

const defaultSelection: AvatarSelection = { photos: [], fixedId: null };
const AvatarContext = createContext<Avatar | null>(null);

function readSelection(value: string | null): AvatarSelection {
  if (!value) return defaultSelection;
  if (value.startsWith(avatarUriPrefix)) {
    return { photos: [{ id: 'original-photo', uri: value }], fixedId: 'original-photo' };
  }

  const saved: Partial<AvatarSelection> = JSON.parse(value);
  const photos = Array.isArray(saved?.photos)
    ? saved.photos.filter(photo => typeof photo?.id === 'string' && typeof photo.uri === 'string'
      && photo.uri.startsWith(avatarUriPrefix)).slice(0, maxAvatarPhotos)
    : [];
  const fixedId = photos.find(photo => photo.id === saved?.fixedId)?.id ?? null;
  return { photos, fixedId };
}

export function AvatarProvider({ owner, children }: PropsWithChildren<{ owner: string }>) {
  const [selection, setSelection] = useState<AvatarSelection>();
  const storageKey = `daytabs.avatar.v1:${owner}`;

  useEffect(() => {
    let active = true;
    AsyncStorage.getItem(storageKey).then(saved => {
      if (active) setSelection(readSelection(saved));
    }).catch(() => {
      if (active) setSelection(defaultSelection);
    });
    return () => { active = false; };
  }, [storageKey]);

  async function save(next: AvatarSelection) {
    await AsyncStorage.setItem(storageKey, JSON.stringify(next));
    setSelection(next);
  }

  return <AvatarContext.Provider value={{ ...(selection ?? defaultSelection), ready: selection !== undefined, save }}>
    {children}
  </AvatarContext.Provider>;
}

export function useAvatar() {
  const avatar = useContext(AvatarContext);
  if (!avatar) throw new Error('Avatar provider is required.');
  return avatar;
}
