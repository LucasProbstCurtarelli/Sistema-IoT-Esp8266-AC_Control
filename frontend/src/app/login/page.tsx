"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
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
            const response = await api.post("/api/login", {
                username: data.username,
                password: data.password,
            });

            if (response.status === 200 && response.data.success) {
                toast.success("Entrou.");
                router.push("/dashboard");
            }
        } catch (error: unknown) {
            const message = readLoginError(error);
            toast.error("Não entrou.", {
                description: message,
            });
        } finally {
            setIsPending(false);
        }
    };

    return (
        <div className="min-h-svh bg-background px-5 pt-16 text-foreground md:pt-24">
            <div className="mx-auto w-full max-w-[22rem]">
                <h1 className="text-4xl font-bold leading-tight">Casa</h1>
                <p className="mt-3 max-w-[28ch] text-muted-foreground">
                    Entra para ver o que está aceso e mandar o ar.
                </p>

                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className="mt-10 space-y-6">
                        <FormField
                            control={form.control}
                            name="username"
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>Usuário</FormLabel>
                                    <FormControl>
                                        <Input
                                            autoComplete="username"
                                            disabled={isPending}
                                            className="h-12 md:text-base"
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
                                    <FormLabel>Senha</FormLabel>
                                    <FormControl>
                                        <PasswordInput
                                            autoComplete="current-password"
                                            disabled={isPending}
                                            className="h-12 md:text-base"
                                            {...field}
                                        />
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />

                        <Button
                            className="h-12 w-full text-base"
                            type="submit"
                            disabled={isPending}
                        >
                            {isPending ? "A entrar" : "Entrar"}
                        </Button>
                    </form>
                </Form>

                <p className="mt-8 text-sm text-muted-foreground">
                    A sessão fica neste aparelho.
                </p>
            </div>
        </div>
    );
}

function readLoginError(error: unknown): string {
    if (!error || typeof error !== "object") return "A casa não respondeu. Tenta outra vez.";
    const response = (error as { response?: { data?: { message?: unknown } } }).response;
    if (!response) return "A casa não respondeu. Tenta outra vez.";
    return typeof response.data?.message === "string" ? response.data.message : "Confere o nome e a senha.";
}
