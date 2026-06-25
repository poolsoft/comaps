package app.organicmaps.carlauncher;

import app.organicmaps.SplashActivity;

/**
 * CarLauncher flavor icin SplashActivity alt sinifi.
 *
 * CarLauncher, Android Auto'nun projected modunu kullanmaz; dogrudan arac ekraninda (head unit) calisir.
 * Orijinal SplashActivity, isCarDisplayUsed() true olunca MapPlaceholderActivity'ye yonlendirir.
 * Bu sinif bu yonlendirmeyi devre disi birakir — harita baslatmaya devam eder.
 *
 * Manifest'te LAUNCHER activity olarak bu sinif kayitlidir.
 */
public class CarLauncherSplashActivity extends SplashActivity
{
  @Override
  protected boolean isCarDisplayRedirectEnabled()
  {
    // CarLauncher dogrudan head unit'te calisir, placeholder'a gitme.
    return false;
  }
}
