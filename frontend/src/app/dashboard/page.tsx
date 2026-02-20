"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Snowflake, Lightbulb } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function DashboardPage() {
  const router = useRouter();

  useEffect(() => {
    // Redireciona para a primeira opção disponível
    router.replace("/dashboard/ar-condicionado");
  }, [router]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-muted-foreground mt-1">
          Selecione um dispositivo para controlar
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card className="cursor-pointer hover:shadow-lg transition-shadow" onClick={() => router.push("/dashboard/ar-condicionado")}>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Snowflake className="h-5 w-5" />
              <CardTitle>Ar Condicionado</CardTitle>
            </div>
            <CardDescription>Controle de temperatura e energia</CardDescription>
          </CardHeader>
          <CardContent>
            <Button className="w-full" variant="outline">
              Acessar
            </Button>
          </CardContent>
        </Card>

        <Card className="cursor-pointer hover:shadow-lg transition-shadow" onClick={() => router.push("/dashboard/iluminacao")}>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Lightbulb className="h-5 w-5" />
              <CardTitle>Iluminação</CardTitle>
            </div>
            <CardDescription>Controle das lâmpadas inteligentes</CardDescription>
          </CardHeader>
          <CardContent>
            <Button className="w-full" variant="outline">
              Acessar
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
