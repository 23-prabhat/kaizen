import { File } from 'expo-file-system';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import { launchImageLibraryAsync } from 'expo-image-picker';
import { Platform } from 'react-native';
import { avatarUriPrefix } from './avatar';

const maxImageBytes = 20 * 1024 * 1024;
const avatarSize = 240;

export async function pickAvatar(): Promise<string | null> {
  const selection = await launchImageLibraryAsync({ mediaTypes: ['images'] });
  const image = selection.assets?.[0];
  if (!image) return null;

  try {
    if ((image.fileSize ?? image.file?.size ?? 0) > maxImageBytes) {
      throw new Error('Choose an image under 20 MB.');
    }
    const edit = ImageManipulator.manipulate(image.uri, { maxWidth: avatarSize, maxHeight: avatarSize });
    try {
      const resized = await edit.renderAsync();
      try {
        const saved = await resized.saveAsync({ format: SaveFormat.PNG, base64: true });
        if (Platform.OS === 'web') URL.revokeObjectURL(saved.uri);
        else {
          try { new File(saved.uri).delete(); } catch { /* The cache can clean up the resized copy later. */ }
        }
        if (!saved.base64) throw new Error('Could not save this image. Try another file.');
        return avatarUriPrefix + saved.base64;
      } finally {
        resized.release();
      }
    } finally {
      edit.release();
    }
  } finally {
    if (Platform.OS === 'web' && image.uri.startsWith('blob:')) URL.revokeObjectURL(image.uri);
  }
}
