package io.github.flymeisland.diagnostic;
/** Right-side content width with a camera exclusion centered on the display. */
public final class CompactSafeArea {
 private CompactSafeArea() {}
 public static int rightWidth(int pillWidth,int exclusionRight,int endPadding) {
  return Math.max(0, pillWidth/2-Math.max(0,exclusionRight)-Math.max(0,endPadding));
 }
}
