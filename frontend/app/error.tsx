"use client";

import { useEffect } from "react";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  // A genuine side effect: reporting, not deriving anything for render.
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="flex flex-col items-start gap-4 py-16">
      <AlertTriangle aria-hidden="true" className="size-8 text-delay-severe" />
      <h1 className="text-title font-semibold">Something went wrong loading this page</h1>
      <p className="max-w-prose text-body text-muted-foreground">
        The flight data service may still be starting up — free-tier instances can take up to a minute
        to wake. Trying again usually works.
      </p>
      <Button onClick={reset}>Try again</Button>
    </div>
  );
}
