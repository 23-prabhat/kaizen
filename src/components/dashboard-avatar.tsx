import { Image, StyleSheet, type ImageStyle, type StyleProp } from 'react-native';
import { actionInk } from '@/lib/theme';

export const mascots = [
  { name: 'Desk cat', source: require('../../assets/desk-cat.png') },
  { name: 'Coffee cat', source: require('../../assets/desk-cat-coffee.png') },
  { name: 'Focus cat', source: require('../../assets/desk-cat-focus.png') },
  { name: 'Victory cat', source: require('../../assets/desk-cat-victory.png') },
];

export function DashboardAvatar({ uri, mascotIndex = 0, style }: {
  uri?: string | null; mascotIndex?: number; style?: StyleProp<ImageStyle>;
}) {
  return <Image source={uri ? { uri } : mascots[mascotIndex].source} resizeMode={uri ? 'cover' : 'contain'}
    accessible={false} aria-hidden style={[style, uri ? styles.custom : undefined]} />;
}

const styles = StyleSheet.create({
  custom: { borderWidth: 2, borderColor: actionInk, borderRadius: 12 },
});
