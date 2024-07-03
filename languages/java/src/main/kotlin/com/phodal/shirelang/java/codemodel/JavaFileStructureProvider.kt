package com.phodal.shirelang.java.codemodel

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil.getChildrenOfTypeAsList
import com.phodal.shirecore.codemodel.FileStructureProvider
import com.phodal.shirecore.codemodel.model.FileStructure

class JavaFileStructureProvider : FileStructureProvider {
    override fun build(psiFile: PsiFile): FileStructure {
        val packageStatement = getChildrenOfTypeAsList(psiFile, PsiPackageStatement::class.java).firstOrNull()
        val importLists = getChildrenOfTypeAsList(psiFile, PsiImportList::class.java)
        val classDeclarations = getChildrenOfTypeAsList(psiFile, PsiClass::class.java)

        val imports = mutableListOf<PsiElement>()
        for (it in importLists) imports.addAll(it.allImportStatements)

        val packageString = packageStatement?.text
        val path = psiFile.virtualFile.path

        return FileStructure(psiFile, psiFile.name, path, packageString, imports, classDeclarations, emptyList())
    }
}
