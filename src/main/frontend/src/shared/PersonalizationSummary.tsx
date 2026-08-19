import { Column, Text } from "@tiendanube/nube-sdk-jsx";
import type { ItemProperties } from "./properties";

type Props = {
	title: string;
	groups: ItemProperties[];
	color?: string;
};

/**
 * Bloco read-only de personalizacoes, usado no checkout e no resumo do carrinho.
 *
 * `Size` nao aceita shorthand CSS ("16px 14px"), nem na prop nem no style, entao
 * todo espacamento composto vai por lado.
 */
export function PersonalizationSummary({ title, groups, color }: Props) {
	return (
		<Column
			gap={0}
			borderRadius="6px"
			style={{
				paddingTop: "16px",
				paddingBottom: "16px",
				paddingLeft: "14px",
				paddingRight: "14px",
				marginTop: "10px",
				border: "1px solid rgba(0,0,0,.12)",
				backgroundColor: "rgba(0,0,0,.025)",
			}}
		>
			<Text
				color={color}
				modifiers={["bold"]}
				style={{ fontSize: "17px", textAlign: "center", marginBottom: "14px" }}
			>
				{title}
			</Text>
			<Column gap={0}>
				{groups.map((group) => (
					<Column
						gap={0}
						style={{
							paddingTop: "14px",
							paddingBottom: "12px",
							borderTop: "1px solid rgba(0,0,0,.08)",
						}}
					>
						<Text
							color={color}
							modifiers={["bold"]}
							style={{ fontSize: "16px", marginBottom: "8px" }}
						>
							{group.productName}
						</Text>
						<Column gap={0}>
							{group.fields.map((field) => (
								<Text
									color={color}
									modifiers={["bold"]}
									style={{ fontSize: "14px", lineHeight: "18px", marginBottom: "2px" }}
								>
									{`${field.name}: ${field.value}`}
								</Text>
							))}
						</Column>
					</Column>
				))}
			</Column>
		</Column>
	);
}
