export function Background() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div className="absolute inset-0 bg-background" />
      {/* Blobs scale up from sm; at full size they swallow a 375px viewport. */}
      <div className="latebird-drift absolute -top-40 -left-32 size-[22rem] rounded-full bg-delay-on-time-surface opacity-50 blur-3xl sm:size-[38rem]" />
      <div
        className="latebird-drift absolute -bottom-48 -right-24 size-[18rem] rounded-full bg-delay-moderate-surface opacity-40 blur-3xl sm:size-[32rem]"
        style={{ animationDelay: "-9s" }}
      />
      <div
        className="latebird-drift absolute top-1/3 left-1/2 size-[15rem] rounded-full bg-delay-minor-surface opacity-30 blur-3xl sm:size-[26rem]"
        style={{ animationDelay: "-17s" }}
      />
    </div>
  );
}
