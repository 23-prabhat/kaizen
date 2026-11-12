const { readFile, writeFile } = require('node:fs/promises');
const { join } = require('node:path');
const { withFinalizedMod } = require('expo/config-plugins');

module.exports = config => withFinalizedMod(config, ['android', async config => {
  // Ask the launcher to redraw even when Daytabs is closed. The widget resolves the quote at render time.
  const path = join(config.modRequest.platformProjectRoot, 'app/src/main/res/xml/daytabs_motivation_info.xml');
  const xml = await readFile(path, 'utf8');
  await writeFile(path, xml.replace(/android:updatePeriodMillis="\d+"/, 'android:updatePeriodMillis="3600000"'));
  return config;
}]);
