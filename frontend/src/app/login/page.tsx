"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { Loader2 } from "lucide-react";
import { LogoBrand } from "@/components/logo-brand";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import {
    Card,
    CardContent,
    CardFooter,
    CardHeader,
} from "@/components/ui/card";
import {
    Form,
    FormControl,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form";
import { api } from "@/services/api";

export default function LoginPage() {
    const router = useRouter();
    const [isPending, setIsPending] = useState(false);

    const form = useForm<{ username: string; password: string }>({
        defaultValues: {
            username: "",
            password: "",
        },
    });

    const onSubmit = async (data: { username: string; password: string }) => {
        setIsPending(true);
        try {
            // For now, simple authentication
            // In production, this should call your Spring Boot auth endpoint
            const response = await api.post("/api/login", {
                username: data.username,
                password: data.password,
            });

            if (response.status === 200 && response.data.success) {
                // JWT token is stored in httpOnly cookie by backend
                // Store user info from response
                const user = response.data.user || {
                    id: "1",
                    name: data.username,
                    email: `${data.username}@automacao.com`,
                };
                document.cookie = `automation.user=${JSON.stringify(user)}; path=/; max-age=86400`;
                
                toast.success("Login realizado com sucesso!");
                router.push("/dashboard");
            }
        } catch (error: any) {
            toast.error("Falha no Login", {
                description: error.response?.data?.message || "Credenciais inválidas",
            });
        } finally {
            setIsPending(false);
        }
    };

    return (
        <div className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
            <div className="w-full max-w-[400px] space-y-6">
                <div className="flex flex-col items-center space-y-2 text-center">
                    <div className="flex h-20 w-20 items-center justify-center rounded-xl bg-transparent shadow-lg transition-all duration-300 ease-in-out hover:scale-105 hover:shadow-xl">
                        <LogoBrand className="h-16 w-16" />
                    </div>
                    <div>
                        <h1 className="text-3xl font-bold tracking-tighter">
                            Automação Residencial
                        </h1>
                        <p className="text-sm font-medium uppercase tracking-widest text-muted-foreground">
                            Painel de Controle
                        </p>
                    </div>
                </div>

                <Card className="border-border/40 shadow-sm">
                    <CardHeader className="pb-2" />

                    <Form {...form}>
                        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
                            <CardContent className="grid gap-4">
                                <FormField
                                    control={form.control}
                                    name="username"
                                    render={({ field }) => (
                                        <FormItem>
                                            <FormLabel>Usuário</FormLabel>
                                            <FormControl>
                                                <Input
                                                    placeholder="admin"
                                                    autoComplete="username"
                                                    disabled={isPending}
                                                    {...field}
                                                />
                                            </FormControl>
                                            <FormMessage />
                                        </FormItem>
                                    )}
                                />

                                <FormField
                                    control={form.control}
                                    name="password"
                                    render={({ field }) => (
                                        <FormItem>
                                            <div className="flex items-center justify-between">
                                                <FormLabel>Senha</FormLabel>
                                            </div>
                                            <FormControl>
                                                <PasswordInput
                                                    placeholder="******"
                                                    autoComplete="current-password"
                                                    disabled={isPending}
                                                    {...field}
                                                />
                                            </FormControl>
                                            <FormMessage />
                                        </FormItem>
                                    )}
                                />
                            </CardContent>

                            <CardFooter className="flex flex-col gap-4">
                                <Button
                                    className="h-10 w-full font-semibold"
                                    type="submit"
                                    disabled={isPending}
                                >
                                    {isPending ? (
                                        <>
                                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                            Validando...
                                        </>
                                    ) : (
                                        "Entrar na Plataforma"
                                    )}
                                </Button>
                            </CardFooter>
                        </form>
                    </Form>
                </Card>

                <p className="text-center text-xs text-muted-foreground">
                    &copy; 2026 Automação Residencial. Todos os direitos reservados.
                </p>
            </div>
        </div>
    );
}
