import { Checkbox, NumberField, Button, Column, Field, Image, Link, Row, Select, Text, Textarea } from "@tiendanube/nube-sdk-jsx";
import type { NubeComponent } from "@tiendanube/nube-sdk-types";
import { styled } from "@tiendanube/nube-sdk-ui";
import { quantityMap, quantityTotal, quantityColumns } from "./quantity-options";
import { messages } from "../shared/i18n";
import type { PersonalizationField } from "../shared/config";
import { type FieldError, type FieldValue, type ValueMap, keyOf } from "./values";

/** Mantenha em sincronia com SDK_MARKER_ID no nuvemshop-personalizer.js. */
export const SDK_MARKER_ID = "ncf-sdk-fields";

function mobileImageGrid(columns: 2 | 3) {
    return styled(Row)`
        @media (max-width:767px) {
            display:grid!important;
            grid-template-columns:repeat(${columns},minmax(0,1fr));
            gap:8px!important;
            & > * {width:auto!important;min-width:0;box-sizing:border-box;padding:6px!important;overflow-wrap:anywhere;}
            & img {width:100%!important;max-width:120px;height:auto!important;aspect-ratio:1;object-fit:contain;}
            & button {min-width:0;max-width:100%;height:auto;min-height:44px;white-space:normal;overflow-wrap:anywhere;}
            & button * {white-space:normal;overflow-wrap:anywhere;}
        }
    `;
}
function quantityGrid(columns: 1 | 2 | 3) {
    return styled(Column)`
        display:grid;grid-template-columns:minmax(0,1fr);column-gap:24px;row-gap:0;
        @media(min-width:700px){grid-template-columns:repeat(auto-fit,minmax(min(100%,max(170px,calc((100% - ${(columns - 1) * 24}px) / ${columns}))),1fr));}
    `;
}
const QuantityGrids = {1:quantityGrid(1),2:quantityGrid(2),3:quantityGrid(3)};
const quantityInk = "var(--main-foreground, #333333)";
const quantitySurface = "var(--main-background, #ffffff)";
const quantityButtonStyle = {
    backgroundColor:quantitySurface,color:quantityInk,border:"1px solid currentColor",borderRadius:"50%",
    width:"34px",height:"34px",minWidth:"34px",minHeight:"34px",padding:"0",fontSize:"18px",lineHeight:"1",boxShadow:"none",
};
const TwoColumnImageGrid = mobileImageGrid(2);
const ThreeColumnImageGrid = mobileImageGrid(3);

type Props = {
    upload?: (field: PersonalizationField) => NubeComponent;
    markerId?: string;
    namePrefix?: string;
	fields: PersonalizationField[];
	errors: FieldError[];
	color?: string;
	values?: ValueMap;
	locale?: string;
	onValueChange: (key: string, value: FieldValue, refresh?: boolean) => void;
};

export function PersonalizationFields({ fields, errors, color, values, locale, onValueChange, upload, markerId = SDK_MARKER_ID, namePrefix = "" }: Props) {
	return (
		// id e o sinal que o script legado usa para se calar e nao duplicar os campos.
		<Column id={markerId} gap={12} style={{ paddingTop: "12px", paddingBottom: "12px" }}>
			{fields.map((field) => (
				<Column key={namePrefix + keyOf(field)} gap={4}>{field.fieldType === "FILE_UPLOAD" && upload ? [upload(field), ...errors.filter(e => e.propertyName === keyOf(field)).map(e => <Text color="#c9252d">{e.message}</Text>)] : fieldGroup(field, errors, color, onValueChange, values?.[keyOf(field)], locale, namePrefix)}</Column>
			))}
		</Column>
	);
}

function fieldGroup(
	field: PersonalizationField,
	errors: FieldError[],
	color: string | undefined,
	onValueChange: (key: string, value: FieldValue, refresh?: boolean) => void,
    selected?: FieldValue, locale?: string, namePrefix = "",
): NubeComponent[] {
	const children: NubeComponent[] = [input(field, color, onValueChange, selected, locale, namePrefix)];
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
	onValueChange: (key: string, value: FieldValue, refresh?: boolean) => void,
    selected?: FieldValue, locale?: string, namePrefix = "",
): NubeComponent {
	const key = keyOf(field);
    const name = namePrefix + key;
	const label = (field.required || field.fieldType === "SELECT") ? `${field.label} *` : field.label;
	// Replacing the SDK slot during typing remounts the focused input.
	// Discrete choices still refresh their selected/checked presentation.
	const handle = (event: { value?: string }) => {
        const value = event.value ?? "";
        const limited = (field.fieldType === "TEXT" || field.fieldType === "TEXTAREA") && field.maxLength != null
            ? value.slice(0, field.maxLength) : value;
        onValueChange(key, limited, field.fieldType === "SELECT" || limited !== value);
    };
	const labelStyle = color ? { color } : undefined;

    if (field.fieldType === "QUANTITY_SELECT") {
        const quantities = quantityMap(selected) ?? {}, total = quantityTotal(quantities);
        const Grid = QuantityGrids[quantityColumns(field)];
        const hint = messages(locale).quantityRange(Math.max(field.required ? 1 : 0, field.minSelections ?? 0),field.maxSelections);
        const children: NubeComponent[] = [<Text color={color}>{label}</Text>];
        if (hint) children.push(<Text color={color} style={{fontSize:"13px",opacity:"0.8"}}>{hint}</Text>);
        children.push(<Grid>{[...new Set(field.options)].map((option, index) => {
            const value = Object.prototype.hasOwnProperty.call(quantities, option) ? quantities[option] : 0;
            // Native max stops only increments beyond the remaining total; existing values can still be reduced.
            const remaining = field.maxSelections == null ? Number.MAX_SAFE_INTEGER : Math.max(value, value + field.maxSelections - total);
            return <NumberField key={`${name}__quantity_${index}`} name={`${name}__quantity_${index}`} label={option}
                value={value} min={0} step={1} max={remaining}
                style={{
                    container:{minWidth:"0",padding:"10px 0",border:"0",borderBottom:"1px solid color-mix(in srgb, var(--main-foreground, #333333) 16%, transparent)",borderRadius:"0",display:"flex",flexDirection:"row",alignItems:"center",justifyContent:"space-between",gap:"12px",backgroundColor:"transparent"},
                    label:{...labelStyle,color:color || quantityInk,fontSize:"14px",lineHeight:"1.4",overflowWrap:"anywhere",minWidth:"0",margin:"0",flex:"1"},
                    wrapper:{display:"flex",alignItems:"center",justifyContent:"center",gap:"6px",width:"110px",minWidth:"110px",flexShrink:"0",padding:"0",border:"0",backgroundColor:"transparent"},
                    input:{color:quantityInk,backgroundColor:"transparent",border:"0",width:"30px",minWidth:"30px",maxWidth:"30px",padding:"0",fontSize:"14px",textAlign:"center",boxShadow:"none"},
                    incrementButton:quantityButtonStyle,decrementButton:quantityButtonStyle,
                }} onChange={event => {
                    onValueChange(key, Object.fromEntries([...new Set(field.options)].map(candidate => [candidate,
                        candidate === option ? Number(event.value ?? 0) : Object.prototype.hasOwnProperty.call(quantities, candidate) ? quantities[candidate] : 0])));
                }} />;
        })}</Grid>,<Text color={color} style={{fontSize:"13px",opacity:"0.85"}}>{messages(locale).quantityCount(total, field.maxSelections)}</Text>);
        return <Column gap={6}>{children}</Column>;
    }
    if (field.fieldType === "CHECKBOX") {
        if (field.options.length) {
            const choices = Array.isArray(selected) ? selected : [];
            const options = [...new Set(field.options)];
            const children: NubeComponent[] = [<Text color={color}>{label}</Text>,
                ...options.map((option, index) => <Checkbox key={`${name}__option_${index}`} name={`${name}__option_${index}`} label={option}
                    checked={choices.includes(option)} style={{label: labelStyle}}
                    onChange={event => {
                        if (event.value && !choices.includes(option) && field.maxSelections != null && choices.length >= field.maxSelections) {
                            onValueChange(key, [...choices]); return;
                        }
                        onValueChange(key, options.filter(candidate => candidate === option ? Number(event.value ?? 0) : choices.includes(candidate)));
                    }} />),
                ...((field.minSelections != null || field.maxSelections != null)
                    ? [<Text color={color}>{messages(locale).selectionCount(choices.length, field.maxSelections)}</Text>,
                        ...(field.minSelections ? [<Text color={color}>{messages(locale).minSelections(field.minSelections)}</Text>] : [])] : [])];
            return <Column gap={8}>{children}</Column>;
        }
        return <Checkbox key={name} name={name} label={label} checked={Boolean(selected)}
            onChange={event => onValueChange(key, event.value ? (locale?.startsWith("es") ? "Sí" : "Sim") : "")}
            style={{ label: labelStyle }} />;
    }
    if (field.fieldType === "COLOR_SELECT") {
        const children: NubeComponent[]=[<Text color={color}>{label}</Text>,
            <Row gap={10} style={{flexWrap:"wrap"}}>{(field.colorOptions ?? []).filter(option=>/^#[0-9a-f]{6}$/i.test(option.color)).map(option=>
                <Button variant="transparent"
                    style={{padding:"10px",border:"1px solid #c8d3d8",borderRadius:"8px",maxWidth:"100%",
                        outline:option.label===selected?"2px solid currentColor":"none",outlineOffset:"2px",color:"inherit"}}
                    ariaLabel={`${label}: ${option.label}${option.label===selected?" ✓":""}`}
                    onClick={()=>onValueChange(key,option.label)}>
                    <Column gap={8} alignItems="center" style={{width:"100%",textAlign:"center"}}>
                        {[<Row style={{backgroundColor:option.color,width:"28px",height:"28px",flexShrink:0,border:"1px solid #777",borderRadius:"50%"}} />,
                            ...(field.showColorNames!==false?[<Text color={color}>{option.label}</Text>]:[])]}
                    </Column>
                </Button>)}</Row>];
        if(!field.required)children.push(<Button variant="link" onClick={()=>onValueChange(key,"")}>{locale?.startsWith("es")?"Borrar selección":"Limpar seleção"}</Button>);
        return <Column gap={8}>{children}</Column>;
    }
    if (field.fieldType === "IMAGE_SELECT") {
        const expand = locale?.startsWith("es") ? "Ampliar imagen" : "Ampliar imagem";
        const ImageGrid = field.mobileImageColumns === 2 ? TwoColumnImageGrid
            : field.mobileImageColumns === 3 ? ThreeColumnImageGrid : Row;
        const cards = <ImageGrid gap={12} style={{flexWrap:"wrap"}}>
            {(field.imageOptions ?? []).map(option => {
                const content: NubeComponent[] = [
                    <Image src={option.thumbnailUrl} alt={option.label} width="120px" height="120px" style={{objectFit:"contain"}} />,
                    <Button variant={option.label === selected ? "primary" : "secondary"}
                    ariaLabel={`${label}: ${option.label}${option.label === selected ? " ✓" : ""}`}
                    onClick={() => onValueChange(key, option.label)}>{option.label === selected ? `✓ ${option.label}` : option.label}</Button>,
                ];
                if (field.showImageZoom !== false) content.push(<Link href={option.imageUrl} target="_blank">{expand}</Link>);
                return <Column width="140px" gap={4}
                    style={{border: option.label === selected ? "2px solid #1976d2" : "1px solid #c8d3d8", borderRadius:"8px", padding:"8px"}}>{content}</Column>;
            })}
        </ImageGrid>;
        const children: NubeComponent[] = [<Text color={color}>{label}</Text>, cards];
        if (!field.required) children.push(<Button variant="link" onClick={() => onValueChange(key, "")}>
            {locale?.startsWith("es") ? "Borrar selección" : "Limpar seleção"}
        </Button>);
        return <Column gap={8}>{children}</Column>;
    }
	if (field.fieldType === "SELECT") {
		return (
			<Select
				key={name} name={name}
				label={label}
				value={typeof selected === "string" ? selected : ""}
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
				key={name} name={name}
				label={label}
				value={typeof selected === "string" ? selected : ""}
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
			key={name} name={name}
			label={label}
			value={typeof selected === "string" ? selected : ""}
			placeholder={field.placeholder ?? undefined}
			onChange={handle}
			style={{ label: labelStyle }}
		/>
	);
}
