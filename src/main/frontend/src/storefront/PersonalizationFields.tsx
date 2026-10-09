import { Button, Column, Field, Image, Link, Row, Select, Text, Textarea } from "@tiendanube/nube-sdk-jsx";
import type { NubeComponent } from "@tiendanube/nube-sdk-types";
import type { PersonalizationField } from "../shared/config";
import { type FieldError, keyOf } from "./values";

/** Mantenha em sincronia com SDK_MARKER_ID no nuvemshop-personalizer.js. */
export const SDK_MARKER_ID = "ncf-sdk-fields";

type Props = {
	fields: PersonalizationField[];
	errors: FieldError[];
	color?: string;
	values?: Record<string, string>;
	locale?: string;
	onValueChange: (key: string, value: string) => void;
};

export function PersonalizationFields({ fields, errors, color, values, locale, onValueChange }: Props) {
	return (
		// id e o sinal que o script legado usa para se calar e nao duplicar os campos.
		<Column id={SDK_MARKER_ID} gap={12} style={{ paddingTop: "12px", paddingBottom: "12px" }}>
			{fields.map((field) => (
				<Column gap={4}>{fieldGroup(field, errors, color, onValueChange, values?.[keyOf(field)], locale)}</Column>
			))}
		</Column>
	);
}

function fieldGroup(
	field: PersonalizationField,
	errors: FieldError[],
	color: string | undefined,
	onValueChange: (key: string, value: string) => void,
    selected?: string, locale?: string,
): NubeComponent[] {
	const children: NubeComponent[] = [input(field, color, onValueChange, selected, locale)];
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
    selected?: string, locale?: string,
): NubeComponent {
	const key = keyOf(field);
	const label = (field.required || field.fieldType === "SELECT") ? `${field.label} *` : field.label;
	const handle = (event: { value?: string }) => onValueChange(key, event.value ?? "");
	const labelStyle = color ? { color } : undefined;

    if (field.fieldType === "IMAGE_SELECT") {
        const expand = locale?.startsWith("es") ? "Ampliar imagen" : "Ampliar imagem";
        const cards = <Row gap={12} style={{flexWrap:"wrap"}}>
            {(field.imageOptions ?? []).map(option => <Column width="140px" gap={4}
                style={{border: option.label === selected ? "2px solid #1976d2" : "1px solid #c8d3d8", borderRadius:"8px", padding:"8px"}}>
                <Image src={option.thumbnailUrl} alt={option.label} width="120px" height="120px" style={{objectFit:"contain"}} />
                <Button variant={option.label === selected ? "primary" : "secondary"}
                    ariaLabel={`${label}: ${option.label}${option.label === selected ? " ✓" : ""}`}
                    onClick={() => onValueChange(key, option.label)}>{option.label === selected ? `✓ ${option.label}` : option.label}</Button>
                <Link href={option.imageUrl} target="_blank">{expand}</Link>
            </Column>)}
        </Row>;
        const children: NubeComponent[] = [<Text color={color}>{label}</Text>, cards];
        if (!field.required) children.push(<Button variant="link" onClick={() => onValueChange(key, "")}>
            {locale?.startsWith("es") ? "Borrar selección" : "Limpar seleção"}
        </Button>);
        return <Column gap={8}>{children}</Column>;
    }
	if (field.fieldType === "SELECT") {
		return (
			<Select
				name={key}
				label={label}
				value={selected ?? ""}
				options={[
					{ label: field.placeholder?.trim() || (locale?.toLowerCase().startsWith("es") ? "Seleccioná una opción" : "Selecione uma opção"), value: "" },
					...field.options.map((option) => ({ label: option, value: option })),
				]}
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
