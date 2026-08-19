import { Column, Field, Select, Text, Textarea } from "@tiendanube/nube-sdk-jsx";
import type { NubeComponent } from "@tiendanube/nube-sdk-types";
import type { PersonalizationField } from "../shared/config";
import { type FieldError, keyOf } from "./values";

type Props = {
	fields: PersonalizationField[];
	errors: FieldError[];
	color?: string;
	onValueChange: (key: string, value: string) => void;
};

export function PersonalizationFields({ fields, errors, color, onValueChange }: Props) {
	return (
		<Column gap={12} style={{ paddingTop: "12px", paddingBottom: "12px" }}>
			{fields.map((field) => (
				<Column gap={4}>{fieldGroup(field, errors, color, onValueChange)}</Column>
			))}
		</Column>
	);
}

function fieldGroup(
	field: PersonalizationField,
	errors: FieldError[],
	color: string | undefined,
	onValueChange: (key: string, value: string) => void,
): NubeComponent[] {
	const children: NubeComponent[] = [input(field, color, onValueChange)];
	const error = errors.find((candidate) => candidate.propertyName === keyOf(field));
	if (error) {
		children.push(
			<Text color="#c9252d" style={{ fontSize: "13px" }}>
				{error.message}
			</Text>,
		);
	}
	return children;
}

function input(
	field: PersonalizationField,
	color: string | undefined,
	onValueChange: (key: string, value: string) => void,
): NubeComponent {
	const key = keyOf(field);
	const label = field.required ? `${field.label} *` : field.label;
	const handle = (event: { value?: string }) => onValueChange(key, event.value ?? "");
	const labelStyle = color ? { color } : undefined;

	if (field.fieldType === "SELECT") {
		return (
			<Select
				name={key}
				label={label}
				options={field.options.map((option) => ({ label: option, value: option }))}
				onChange={handle}
				style={{ label: labelStyle }}
			/>
		);
	}
	if (field.fieldType === "TEXTAREA") {
		return (
			<Textarea
				name={key}
				label={label}
				maxLength={field.maxLength ?? undefined}
				onChange={handle}
				style={{ label: labelStyle }}
			/>
		);
	}
	// TEXT e NUMBER usam o mesmo componente: NumberField nao aceita a mascara/regex
	// que o lojista configura, entao NUMBER e validado em values.ts.
	return (
		<Field
			name={key}
			label={label}
			placeholder={field.placeholder ?? undefined}
			onChange={handle}
			style={{ label: labelStyle }}
		/>
	);
}
