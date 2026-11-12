import { getRandomValues } from 'expo-crypto';
import { useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { DashboardAvatar, mascots } from '@/components/dashboard-avatar';
import { Action, Copy, Icon, IconButton, Message, Panel } from '@/components/ui';
import { maxAvatarPhotos, useAvatar, type AvatarSelection } from '@/lib/avatar';
import { pickAvatar } from '@/lib/pick-avatar';
import { actionInk, useAppearance } from '@/lib/theme';

export function AvatarSettings() {
  const { photos, fixedId, ready, save } = useAvatar();
  const { colors } = useAppearance();
  const running = useRef(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [error, setError] = useState('');
  const disabled = busy || !ready;

  async function update(build: () => AvatarSelection | null | Promise<AvatarSelection | null>, message: string) {
    if (running.current) return;
    running.current = true;
    setBusy(true);
    setError('');
    setFeedback('');
    try {
      const next = await build();
      if (!next) return;
      await save(next);
      setFeedback(message);
    } catch (failure) {
      setError(failure instanceof Error && failure.message === 'Choose an image under 20 MB.'
        ? failure.message
        : 'Could not update the avatars. Try again or choose another image.');
    } finally {
      running.current = false;
      setBusy(false);
    }
  }

  function addImage(useImage: boolean) {
    if (photos.length >= maxAvatarPhotos) return;
    void update(async () => {
      const uri = await pickAvatar();
      if (!uri) return null;
      const id = getRandomValues(new Uint32Array(2)).join('-');
      return { photos: [...photos, { id, uri }], fixedId: useImage ? id : fixedId };
    }, useImage ? 'Using your image.' : 'Image added.');
  }

  function useSingleImage() {
    if (fixedId) return;
    if (photos.length === 0) {
      addImage(true);
      return;
    }
    void update(() => ({ photos, fixedId: photos[0].id }), 'Using one image.');
  }

  return <Panel style={styles.panel}>
    <View accessibilityRole="radiogroup" accessibilityLabel="Avatar mode" style={styles.modes}>
      {[
        { name: 'Rotate avatars', selected: !fixedId, onPress: () => {
          if (fixedId) void update(() => ({ photos, fixedId: null }), 'Avatar rotation on.');
        } },
        { name: 'Use one image', selected: Boolean(fixedId), onPress: useSingleImage },
      ].map(({ name, selected, onPress }) => <Pressable key={name} accessibilityRole="radio" accessibilityLabel={name}
        accessibilityState={{ checked: selected, disabled }} disabled={disabled} onPress={onPress}
        style={[styles.mode, { backgroundColor: selected ? colors.action : colors.surface,
          borderColor: selected ? actionInk : colors.ink }]}>
        <Copy style={[styles.modeLabel, selected && { color: actionInk }]}>{name}</Copy>
      </Pressable>)}
    </View>
    <Copy secondary style={styles.description}>{fixedId
      ? 'Your selected image stays on the dashboard.'
      : 'The four mascots and your images take turns when you open the app.'}</Copy>
    {!fixedId && <View style={styles.gallery}>
      {mascots.map(({ name }, index) => <View key={name} accessible accessibilityLabel={name}
        style={[styles.mascot, { backgroundColor: colors.cover }]}>
        <DashboardAvatar mascotIndex={index} style={styles.mascotImage} />
      </View>)}
    </View>}
    {photos.length > 0 && <View>
      <Copy style={styles.galleryTitle}>Your images</Copy>
      {photos.map(({ id, uri }, index) => <View key={id} style={[styles.photoRow, { borderTopColor: colors.divider }]}>
        <Pressable accessibilityRole="button" accessibilityLabel={`Use image ${index + 1}`}
          accessibilityState={{ selected: fixedId === id, disabled }} disabled={disabled}
          onPress={() => void update(() => ({ photos, fixedId: id }), `Using image ${index + 1}.`)}
          style={({ pressed }) => [styles.photo, pressed && { opacity: 0.7 }]}>
          <DashboardAvatar uri={uri} style={styles.thumbnail} />
          <Copy style={styles.photoLabel}>Image {index + 1}</Copy>
          {fixedId === id && <Icon name="check" size={18} color={colors.ink} />}
        </Pressable>
        <IconButton icon="x" label={`Remove image ${index + 1}`} disabled={disabled}
          onPress={() => void update(() => ({
            photos: photos.filter(photo => photo.id !== id),
            fixedId: fixedId === id ? null : fixedId,
          }), 'Image removed.')} />
      </View>)}
    </View>}
    <Action label="Add image" icon="image" disabled={disabled || photos.length >= maxAvatarPhotos}
      onPress={() => addImage(false)} />
    {photos.length === maxAvatarPhotos && <Copy secondary style={styles.description}>Remove an image to add another.</Copy>}
    {feedback ? <View accessibilityLiveRegion="polite"><Copy>{feedback}</Copy></View> : null}
    {error ? <Message>{error}</Message> : null}
  </Panel>;
}

const styles = StyleSheet.create({
  panel: { gap: 9, padding: 12 },
  modes: { flexDirection: 'row', gap: 6 },
  mode: { flex: 1, minHeight: 44, justifyContent: 'center', padding: 6, borderRadius: 7, borderWidth: 2 },
  modeLabel: { textAlign: 'center', fontWeight: '700', fontSize: 14, lineHeight: 19 },
  description: { fontSize: 14, lineHeight: 19 },
  gallery: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  mascot: { width: 48, height: 48, padding: 3, borderRadius: 7 },
  mascotImage: { width: '100%', height: '100%' },
  galleryTitle: { fontSize: 14, fontWeight: '700', marginBottom: 4 },
  photoRow: { flexDirection: 'row', alignItems: 'center', borderTopWidth: 1 },
  photo: { flex: 1, minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 4 },
  thumbnail: { width: 38, height: 38 },
  photoLabel: { flex: 1, fontSize: 14 },
});
