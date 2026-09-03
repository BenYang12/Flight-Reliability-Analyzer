import Link from "next/link";
import { SearchX } from "lucide-react";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <div className="flex flex-col items-start gap-4 py-16">
      <SearchX aria-hidden="true" className="size-8 text-muted-foreground" />
      <h1 className="text-title font-semibold">We have no record of that flight</h1>
      <p className="max-w-prose text-body text-muted-foreground">
        LateBird covers US domestic flights reported to the Bureau of Transportation Statistics, which
        lags about two months behind. Check the flight number, or try the route instead.
      </p>
      <Button render={<Link href="/" />}>Search again</Button>
    </div>
  );
}
