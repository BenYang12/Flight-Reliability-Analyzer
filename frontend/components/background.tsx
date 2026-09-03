export function Background() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div className="absolute inset-0 bg-background" />
      <div className="latebird-drift absolute -top-40 -left-32 h-[38rem] w-[38rem] rounded-full bg-delay-on-time-surface opacity-50 blur-3xl" />
      <div
        className="latebird-drift absolute -bottom-48 -right-24 h-[32rem] w-[32rem] rounded-full bg-delay-moderate-surface opacity-40 blur-3xl"
        style={{ animationDelay: "-9s" }}
      />
      <div
        className="latebird-drift absolute top-1/3 left-1/2 h-[26rem] w-[26rem] rounded-full bg-delay-minor-surface opacity-30 blur-3xl"
        style={{ animationDelay: "-17s" }}
      />
    </div>
  );
}
